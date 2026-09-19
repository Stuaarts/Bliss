param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Copy .env.example to .env and set a local password first.' }
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^(BLISS_(?:DB_(?:USER|PASSWORD|URL|PORT)|API_PORT))=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
Push-Location $root
try {
    docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL did not become healthy. Check Docker Desktop.' }
    & ./services/api/mvnw.cmd -f services/api/pom.xml clean quarkus:dev '-Dquarkus.profile=local'
    if ($LASTEXITCODE -ne 0) { throw 'API process failed.' }
} finally { Pop-Location }
