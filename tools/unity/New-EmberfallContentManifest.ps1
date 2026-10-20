[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ContentVersion,

    [string]$PackRoot,
    [switch]$BossesOnly
)

$ErrorActionPreference = 'Stop'
if (-not $PackRoot) { $PackRoot = Join-Path $PSScriptRoot '../../content-pack/generated' }
$artRoot = Join-Path $PackRoot 'art'
$assetFiles = [ordered]@{
    'tileset'            = 'tileset.png'
    'sprite.player'      = 'player.png'
    'sprite.gloomfang'   = 'gloomfang.png'
    'sprite.stonewarden' = 'stonewarden.png'
    'sprite.mirewitch'   = 'mirewitch.png'
    'sprite.ashtitan'    = 'ashtitan.png'
    'sprite.hollowking'  = 'hollowking.png'
    'sprite.dragon'      = 'dragon.png'
}
if ($BossesOnly) {
    foreach ($id in @('tileset', 'sprite.player')) { $assetFiles.Remove($id) }
}

if ($ContentVersion -notmatch '^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$') {
    throw 'ContentVersion may contain only letters, digits, dot, underscore, colon, and hyphen.'
}

$assets = foreach ($entry in $assetFiles.GetEnumerator()) {
    $file = Join-Path $artRoot $entry.Value
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing generated asset: $file" }
    $item = Get-Item -LiteralPath $file
    [ordered]@{
        id     = $entry.Key
        path   = "art/$($entry.Value)"
        sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes  = [Int64]$item.Length
    }
}

$manifest = [ordered]@{
    schemaVersion     = 1
    contentVersion    = $ContentVersion
    minimumAppVersion = 1
    assets            = @($assets)
}

$output = Join-Path $PackRoot 'manifest.json'
$json = $manifest | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($output, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote $output"
