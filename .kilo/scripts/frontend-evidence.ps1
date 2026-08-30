# .kilo/scripts/frontend-evidence.ps1
# Gera docs/evidence/08-frontend.png a partir das paginas reais renderizadas:
# 1) FrontendEvidenceDumpTest renderiza as 3 telas (form, resultado HIGH, trace) em target/frontend-evidence/*.html
# 2) Edge headless captura screenshots
# 3) System.Drawing monta a imagem composta com titulos
# Uso (da raiz do repo):  powershell -ExecutionPolicy Bypass -File .kilo/scripts/frontend-evidence.ps1

$ErrorActionPreference = "Stop"

$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidenceDir = Join-Path $root "target\frontend-evidence"
$outputPng = Join-Path $root "docs\evidence\08-frontend.png"

Write-Host "[1/3] Renderizando paginas (FrontendEvidenceDumpTest)..."
Push-Location $root
try {
    & .\mvnw.cmd -q test "-Dtest=FrontendEvidenceDumpTest" "-Dfrontend.evidence.dump=true"
    if ($LASTEXITCODE -ne 0) { throw "FrontendEvidenceDumpTest falhou" }
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
    @{ html = "form.html";         png = "form.png";         title = "1. Formulario de solicitacao (GET /)" },
    @{ html = "result-high.html";  png = "result-high.png";  title = "2. Resultado da analise HIGH com aprovacao humana (GET /requests/{id})" },
    @{ html = "trace.html";        png = "trace.png";        title = "3. Trace da execucao com documentos recuperados (GET /traces/{traceId})" }
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
    $item.png = $pngPath
}

Write-Host "[3/3] Montando $outputPng..."
Add-Type -AssemblyName System.Drawing

$padding = 24
$titleHeight = 52
$fonts = @{
    title = New-Object System.Drawing.Font("Segoe UI", 14, [System.Drawing.FontStyle]::Bold)
}

$images = @()
$totalHeight = 0
$maxWidth = 0
foreach ($item in $screens) {
    $img = [System.Drawing.Image]::FromFile($item.png)
    $images += ,@($item, $img)
    $totalHeight += $titleHeight + $img.Height
    if ($img.Width -gt $maxWidth) { $maxWidth = $img.Width }
}
$canvasWidth = $maxWidth + 2 * $padding
$canvasHeight = $totalHeight + ($screens.Count + 1) * $padding

$bmp = New-Object System.Drawing.Bitmap($canvasWidth, $canvasHeight)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 30, 41, 59))

$y = $padding
foreach ($entry in $images) {
    $item = $entry[0]
    $img = $entry[1]
    $g.DrawString($item.title, $fonts.title, $brush, $padding, $y)
    $y += $titleHeight
    $g.DrawImage($img, $padding, $y, $img.Width, $img.Height)
    $y += $img.Height + $padding
    $img.Dispose()
}

$g.Dispose()
$brush.Dispose()
$bmp.Save($outputPng, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Host "Evidencia gerada: $outputPng"
