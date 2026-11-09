param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.tools'
$mavenHome = Join-Path $toolsRoot 'apache-maven-3.9.9'
$mavenExe = Join-Path $mavenHome 'bin\mvn.cmd'
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Program Files\Android\openjdk\jdk-21.0.8')) { $env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8' }
if (-not (Test-Path $mavenExe)) {
    New-Item -ItemType Directory -Force $toolsRoot | Out-Null
    $archivePath = Join-Path $toolsRoot 'maven-3.9.9.zip'
    $artifactUrl = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'
    Invoke-WebRequest -Uri $artifactUrl -OutFile $archivePath
    $expectedHash = ((Invoke-WebRequest -Uri "$artifactUrl.sha512").Content.Trim() -split '\s+')[0]
    $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA512).Hash
    if ($expectedHash -notmatch '^[a-fA-F0-9]{128}$' -or $actualHash -ne $expectedHash) { throw 'Maven SHA-512 verification failed' }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsRoot -Force
}
Push-Location $projectRoot
try { & $mavenExe @MavenArgs; exit $LASTEXITCODE }
finally { Pop-Location }
