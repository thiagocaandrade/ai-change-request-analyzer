# .kilo/scripts/kanban.ps1
# Helpers do fluxo Kanban GitHub (Projects v2 + Issues) via gh CLI.
# Uso: dot-source a partir da raiz do repo:  . .kilo/scripts/kanban.ps1

$KANBAN_OWNER        = "IA-para-DEVs-SCTEC-T2"
$KANBAN_REPO         = "ai-change-request-analyzer"
$KANBAN_PROJECT_NUM  = 62
$KANBAN_PROJECT_ID   = "PVT_kwDOEJ21384Bhli7"
$KANBAN_STATUS_FIELD = "PVTSSF_lADOEJ21384Bhli7zhggvws"

$KANBAN_STATUS = @{
    Backlog    = "f75ad846"
    Ready      = "61e4505c"
    InProgress = "47fc9ee4"
    InReview   = "df73e18b"
    Done       = "98236657"
}

$KANBAN_FLOW_DIR = Join-Path (Split-Path $PSScriptRoot -Parent) "flow"

function Assert-GhAuth {
    & gh auth status *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "gh CLI nao autenticado. Rode: gh auth login --scopes repo,project"
    }
    & gh project view $KANBAN_PROJECT_NUM --owner $KANBAN_OWNER *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Sem acesso ao projeto $KANBAN_PROJECT_NUM do owner $KANBAN_OWNER"
    }
}

function New-KanbanIssue {
    param(
        [Parameter(Mandatory = $true)][string]$Title,
        [string]$Body = "",
        [string]$Label = ""
    )
    $ghArgs = @("issue", "create", "--repo", "$KANBAN_OWNER/$KANBAN_REPO", "--title", $Title, "--body", $Body)
    if ($Label) { $ghArgs += @("--label", $Label) }
    $out = & gh @ghArgs
    if ($LASTEXITCODE -ne 0) { throw "gh issue create falhou: $out" }
    if ($out -match '/(\d+)\s*$') { return [int]$Matches[1] }
    throw "Nao foi possivel extrair o numero da issue de: $out"
}

function Find-KanbanItem {
    param([Parameter(Mandatory = $true)][int]$IssueNumber)
    $query = @'
query($project: ID!) {
  node(id: $project) {
    ... on ProjectV2 {
      items(first: 100) {
        nodes {
          id
          content { ... on Issue { number } }
        }
      }
    }
  }
}
'@
    $json = (& gh api graphql -f query=$query -F project=$KANBAN_PROJECT_ID | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Find-KanbanItem: falha na query GraphQL" }
    $data = $json | ConvertFrom-Json
    if ($data.errors) { throw "Find-KanbanItem: $($data.errors.message -join '; ')" }
    $item = $data.data.node.items.nodes |
        Where-Object { $_.content.number -eq $IssueNumber } |
        Select-Object -First 1
    if ($item) { return $item.id }
    return $null
}

function Add-KanbanItem {
    param([Parameter(Mandatory = $true)][int]$IssueNumber)
    $existing = Find-KanbanItem -IssueNumber $IssueNumber
    if ($existing) { return $existing }
    $url = "https://github.com/$KANBAN_OWNER/$KANBAN_REPO/issues/$IssueNumber"
    $out = & gh project item-add $KANBAN_PROJECT_NUM --owner $KANBAN_OWNER --url $url
    if ($LASTEXITCODE -ne 0) { throw "gh project item-add falhou: $out" }
    if ($out -match '(PVTI_[A-Za-z0-9_]+)') { return $Matches[1] }
    $found = Find-KanbanItem -IssueNumber $IssueNumber
    if ($found) { return $found }
    throw "Nao foi possivel obter o itemId da issue #$IssueNumber"
}

function Set-KanbanStatus {
    param(
        [Parameter(Mandatory = $true)][string]$ItemId,
        [Parameter(Mandatory = $true)]
        [ValidateSet("Backlog", "Ready", "InProgress", "InReview", "Done")]
        [string]$Status
    )
    $optionId = $KANBAN_STATUS[$Status]
    $mutation = 'mutation($project:ID!,$item:ID!,$field:ID!,$value:String!){updateProjectV2ItemFieldValue(input:{projectId:$project,itemId:$item,fieldId:$field,value:{singleSelectOptionId:$value}}){clientMutationId}}'
    $out = & gh api graphql -f query=$mutation -F project=$KANBAN_PROJECT_ID -F item=$ItemId -F field=$KANBAN_STATUS_FIELD --raw-field value="$optionId"
    if ($LASTEXITCODE -ne 0) { throw "Set-KanbanStatus falhou: $out" }
}

function Close-KanbanIssue {
    param(
        [Parameter(Mandatory = $true)][int]$IssueNumber,
        [string]$ItemId = ""
    )
    if ($ItemId) { Set-KanbanStatus -ItemId $ItemId -Status Done }
    & gh issue close $IssueNumber --repo "$KANBAN_OWNER/$KANBAN_REPO" *> $null
    if ($LASTEXITCODE -ne 0) { throw "gh issue close falhou (#$IssueNumber)" }
}

function Link-KanbanSubIssues {
    param(
        [Parameter(Mandatory = $true)][int]$ParentNumber,
        [Parameter(Mandatory = $true)][int[]]$SubNumbers
    )
    $current = ((& gh issue view $ParentNumber --repo "$KANBAN_OWNER/$KANBAN_REPO" --json body -q .body | Out-String).TrimEnd())
    if ($LASTEXITCODE -ne 0) { throw "gh issue view falhou (#$ParentNumber)" }
    $lines = $SubNumbers | ForEach-Object { "- [ ] #$_" }
    $newBody = (@($current) + $lines) -join "`n"
    $tmp = [System.IO.Path]::GetTempFileName()
    Set-Content -LiteralPath $tmp -Value $newBody -Encoding UTF8
    try {
        & gh issue edit $ParentNumber --repo "$KANBAN_OWNER/$KANBAN_REPO" --body-file $tmp *> $null
        if ($LASTEXITCODE -ne 0) { throw "gh issue edit falhou (#$ParentNumber)" }
    }
    finally {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
}

function Get-FlowState {
    param([Parameter(Mandatory = $true)][string]$Change)
    $path = Join-Path $KANBAN_FLOW_DIR "$Change.json"
    if (Test-Path -LiteralPath $path) {
        return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
    }
    return $null
}

function Set-FlowState {
    param(
        [Parameter(Mandatory = $true)][string]$Change,
        [Parameter(Mandatory = $true)]$State
    )
    if (-not (Test-Path -LiteralPath $KANBAN_FLOW_DIR)) {
        New-Item -ItemType Directory -Path $KANBAN_FLOW_DIR | Out-Null
    }
    $path = Join-Path $KANBAN_FLOW_DIR "$Change.json"
    $State | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $path -Encoding UTF8
}
