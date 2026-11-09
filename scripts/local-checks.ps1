param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome -and (Test-Path 'C:\Program Files\Android\openjdk\jdk-21.0.8')) { $JavaHome = 'C:\Program Files\Android\openjdk\jdk-21.0.8' }
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java' }
$classes = Join-Path $projectRoot '.work\checkclasses'
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @('shared/src/main/java/ru/mephi/shared/DomainException.java','shared/src/main/java/ru/mephi/shared/DateWindow.java','shared/src/main/java/ru/mephi/shared/RoomView.java','shared/src/main/java/ru/mephi/shared/RoomRanking.java','shared/src/main/java/ru/mephi/shared/TraceIds.java','scripts/checks/CoreChecks.java','scripts/checks/SourceChecks.java') | ForEach-Object { Join-Path $projectRoot $_ }
& $javac --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
$output = @(& $java -cp $classes CoreChecks)
if ($LASTEXITCODE -ne 0) { throw 'Core checks failed' }
$output += & $java -cp $classes SourceChecks $projectRoot
if ($LASTEXITCODE -ne 0) { throw 'Syntax checks failed' }
$output | ForEach-Object { Write-Host $_ }
New-Item -ItemType Directory -Force (Join-Path $projectRoot 'evidence') | Out-Null
@("UTC: $([DateTime]::UtcNow.ToString('o'))", 'Scope: dependency-free core and Java syntax only', $output) | Set-Content -Encoding utf8 (Join-Path $projectRoot 'evidence\local-checks.txt')
