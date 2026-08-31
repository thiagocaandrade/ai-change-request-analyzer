# .kilo/scripts/devops-evidence.ps1
# Gera as evidencias de DevOps a partir de saidas reais do sistema:
# 1) DevOpsEvidenceDumpTest renderiza em target/devops-evidence/*.html:
#    - ci.html: estagios do pipeline real (.github/workflows/ci.yml), redacao de logs e
#      resposta real de POST /api/devops/log-analysis;
#    - anomaly.html: relatorios reais de POST /api/devops/runs (baseline 400ms -> 2800ms HIGH,
#      tendencia de falha em janela de 5);
#    - n8n.html: nos e conexoes do n8n/workflow.json real;
# 2) Edge headless captura screenshots;
# 3) System.Drawing monta as imagens compostas 11-github-actions.png, 12-anomaly.png e 13-n8n.png.
# Uso (da raiz do repo):  powershell -ExecutionPolicy Bypass -File .kilo/scripts/devops-evidence.ps1

$ErrorActionPreference = "Stop"

$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidenceDir = Join-Path $root "target\devops-evidence"
$outputDir = Join-Path $root "docs\evidence"

Write-Host "[1/3] Renderizando paginas (DevOpsEvidenceDumpTest)..."
Push-Location $root
try {
    & .\mvnw.cmd -q test "-Dtest=DevOpsEvidenceDumpTest" "-Ddevops.evidence.dump=true"
    if ($LASTEXITCODE -ne 0) { throw "DevOpsEvidenceDumpTest falhou" }
} finally {
    Pop-Location
}

$edgeCandidates = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Google\Chrome\Application\chrome.exe"
)
$browser = $edgeCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $browser) { throw "Nenhum browser headless encontrado (msedge/chrome)" }

Write-Host "[2/3] Capturando screenshots com $browser..."
$screens = @(
    @{ html = "ci.html";      png = "ci.png";      title = "1. CI pipeline: estagios compile - unit - integration - quality - Docker, artefatos build.log/test.log e analise de logs com IA" },
    @{ html = "anomaly.html"; png = "anomaly.png"; title = "2. Deteccao de anomalia (baseline 400ms vs observado 2800ms = HIGH) e tendencia de falha em janela de 5 execucoes" },
    @{ html = "n8n.html";     png = "n8n.png";     title = "3. Workflow n8n: webhook -> POST /api/change-requests -> IF risk == HIGH -> notificacao (sem logica de negocio)" }
)

foreach ($item in $screens) {
    $htmlPath = Join-Path $evidenceDir $item.html
    if (-not (Test-Path -LiteralPath $htmlPath)) { throw "Faltando $htmlPath" }
    $pngPath = Join-Path $evidenceDir $item.png
    $fileUrl = "file:///" + ($htmlPath -replace "\\", "/")
    $outLog = Join-Path $evidenceDir "edge-out.log"
    $errLog = Join-Path $evidenceDir "edge-err.log"
    $browserArgs = @(
        "--headless", "--disable-gpu", "--hide-scrollbars", "--virtual-time-budget=2000",
        "--window-size=1280,1600", "--screenshot=$pngPath", $fileUrl
    )
    Start-Process -FilePath $browser -ArgumentList $browserArgs -Wait -NoNewWindow `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog | Out-Null
    if (-not (Test-Path -LiteralPath $pngPath)) { throw "Screenshot nao gerado: $pngPath" }
}

Write-Host "[3/3] Montando evidencias em $outputDir..."
Add-Type -AssemblyName System.Drawing

$evidenceNames = @("11-github-actions", "12-anomaly", "13-n8n")
for ($i = 0; $i -lt $screens.Count; $i++) {
    $item = $screens[$i]
    $outputPng = Join-Path $outputDir "$($evidenceNames[$i]).png"
    $srcPng = Join-Path $evidenceDir $item.png
    $img = [System.Drawing.Image]::FromFile($srcPng)

    $padding = 24
    $titleHeight = 48
    $canvasWidth = $img.Width + 2 * $padding
    $canvasHeight = $img.Height + $titleHeight + 2 * $padding

    $bmp = New-Object System.Drawing.Bitmap($canvasWidth, $canvasHeight)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::White)
    $font = New-Object System.Drawing.Font("Segoe UI", 13, [System.Drawing.FontStyle]::Bold)
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 30, 41, 59))
    $g.DrawString($item.title, $font, $brush, $padding, 14)
    $g.DrawImage($img, $padding, $titleHeight, $img.Width, $img.Height)

    $g.Dispose(); $brush.Dispose(); $font.Dispose()
    $bmp.Save($outputPng, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose(); $img.Dispose()
    Write-Host "Evidencia gerada: $outputPng"
}
