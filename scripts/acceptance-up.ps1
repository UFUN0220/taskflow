[CmdletBinding()]
param(
    [switch]$SkipCheck,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.acceptance.yml' | Resolve-Path

$required = @(
    'TASKFLOW_ACCEPTANCE_DB_ROOT_PASSWORD',
    'TASKFLOW_ACCEPTANCE_DB_USERNAME',
    'TASKFLOW_ACCEPTANCE_DB_PASSWORD',
    'TASKFLOW_ACCEPTANCE_RABBITMQ_USERNAME',
    'TASKFLOW_ACCEPTANCE_RABBITMQ_PASSWORD',
    'TASKFLOW_ACCEPTANCE_MINIO_ROOT_USER',
    'TASKFLOW_ACCEPTANCE_MINIO_ROOT_PASSWORD',
    'TASKFLOW_ACCEPTANCE_JWT_SECRET',
    'TASKFLOW_ACCEPTANCE_ADMIN_USERNAME',
    'TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD',
    'TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD'
)
foreach ($name in $required) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required acceptance environment variable: $name"
    }
}

Write-Output 'Validating isolated acceptance Compose configuration...'
docker compose -f $composeFile config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Acceptance Compose configuration is invalid.' }

if (-not $SkipBuild) {
    $jar = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '..\target') -Filter 'taskflow-backend-*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    if ($null -eq $jar) {
        Write-Output 'Acceptance JAR not found; building it with the project Maven Wrapper...'
        & (Join-Path $PSScriptRoot '..\mvnw.cmd') -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Acceptance JAR build failed.' }
    }
    Write-Output 'Building isolated acceptance images (volumes are preserved)...'
    docker compose -f $composeFile build
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance image build failed.' }
}

Write-Output 'Starting isolated acceptance services (volumes are preserved)...'
docker compose -f $composeFile up -d
if ($LASTEXITCODE -ne 0) { throw 'Acceptance Compose startup failed.' }

if (-not $SkipCheck) {
    & (Join-Path $PSScriptRoot 'acceptance-check.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance smoke check failed.' }
}

Write-Output 'Acceptance environment is ready.'
