$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$manifest = Join-Path $projectRoot 'evidence\runtime\processes.json'
if (-not (Test-Path $manifest)) { Write-Host 'No recorded processes'; exit 0 }
foreach ($entry in (Get-Content -Raw $manifest | ConvertFrom-Json)) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -match '^java' -and $process.StartTime.ToUniversalTime().ToString('o') -eq $entry.startedAt) {
        Stop-Process -Id $entry.pid
    }
}
