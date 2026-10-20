[CmdletBinding()]
param(
    [string]$UnityPath,
    [string]$ContentVersion = ("art-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)

$ErrorActionPreference = 'Stop'
$toolRoot = $PSScriptRoot
$projectPath = Join-Path $toolRoot 'EmberfallArtProject'
$logPath = Join-Path $toolRoot 'emberfall-art-build.log'

function Find-UnityEditor {
    $roots = @('C:\Program Files\Unity\Hub\Editor', 'C:\Program Files\Unity Hub\Editor')
    $candidates = foreach ($root in $roots) {
        if (Test-Path -LiteralPath $root) {
            Get-ChildItem -LiteralPath $root -Directory | ForEach-Object {
                Join-Path $_.FullName 'Editor\Unity.exe'
            }
        }
    }
    $editor = $candidates | Where-Object { Test-Path -LiteralPath $_ } |
        Sort-Object { (Get-Item -LiteralPath $_).LastWriteTimeUtc } -Descending | Select-Object -First 1
    if (-not $editor) { throw 'Unity.exe was not found. Pass -UnityPath with its full path.' }
    return $editor
}

if (-not $UnityPath) { $UnityPath = Find-UnityEditor }
if (-not (Test-Path -LiteralPath $UnityPath -PathType Leaf)) { throw "Unity editor not found: $UnityPath" }

# Unity creates an empty project when given a project path. The editor script in
# Assets/Editor is already present, so no manual scene or package setup is needed.
$arguments = @(
    '-batchmode', '-accept-apiupdate', '-quit',
    '-projectPath', $projectPath,
    '-executeMethod', 'EmberfallResourceBuilder.BuildAll',
    '-logFile', $logPath
)
& $UnityPath @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Unity art generation failed (exit $LASTEXITCODE). See $logPath"
}

& (Join-Path $toolRoot 'New-EmberfallContentManifest.ps1') -ContentVersion $ContentVersion
Write-Host "Ready to upload: $(Join-Path $toolRoot '../../content-pack/generated')"
