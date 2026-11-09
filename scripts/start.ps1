$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & ./mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } elseif (Test-Path 'C:\Program Files\Android\openjdk\jdk-21.0.8\bin\java.exe') { 'C:\Program Files\Android\openjdk\jdk-21.0.8\bin\java.exe' } else { 'java.exe' }
    $runtime = Join-Path $projectRoot 'evidence\runtime'
    New-Item -ItemType Directory -Force $runtime | Out-Null
    $processes = @()
    foreach ($module in @('discovery-server','hotel-service','booking-service','api-gateway')) {
        $jarPath = Join-Path $projectRoot "$module\target\$module-1.0.0.jar"
        $process = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$jarPath`"") -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime "$module.log") -RedirectStandardError (Join-Path $runtime "$module.stderr.log")
        $processes += @{module=$module;pid=$process.Id;startedAt=$process.StartTime.ToUniversalTime().ToString('o')}
    }
    $processes | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $runtime 'processes.json')
    Write-Host 'Started. Wait for readiness: python scripts/e2e.py'
} finally { Pop-Location }
