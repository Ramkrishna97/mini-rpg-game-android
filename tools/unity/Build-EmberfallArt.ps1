[CmdletBinding()]
param(
    [string]$UnityPath,
    [string]$ProjectPath = (Join-Path $PSScriptRoot 'EmberfallArtProject'),
    [string]$ContentVersion = ("art-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)

$ErrorActionPreference = 'Stop'
$toolRoot = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $toolRoot '../..')).Path
$projectPath = (Resolve-Path $ProjectPath).Path
$outputRoot = Join-Path $repoRoot 'content-pack/generated'
$logPath = Join-Path $toolRoot 'emberfall-art-build.log'

function Find-UnityEditor {
    $roots = @('C:\Program Files\Unity\Hub\Editor', 'C:\Program Files\Unity Hub\Editor',
               'F:\Apps\Unity engine\Unity Hub\Installs')
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
    '-emberfallOutput', $outputRoot,
    '-logFile', $logPath
)
$argumentLine = ($arguments | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
$unityProcess = Start-Process -FilePath $UnityPath -ArgumentList $argumentLine -Wait -PassThru -WindowStyle Hidden
if ($unityProcess.ExitCode -ne 0) {
    throw "Unity art generation failed (exit $($unityProcess.ExitCode)). See $logPath"
}

& (Join-Path $toolRoot 'New-EmberfallContentManifest.ps1') -ContentVersion $ContentVersion -BossesOnly
Write-Host "Ready to upload: $(Join-Path $toolRoot '../../content-pack/generated')"
