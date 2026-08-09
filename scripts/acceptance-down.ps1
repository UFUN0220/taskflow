[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.acceptance.yml' | Resolve-Path

# Compose interpolates the file even for `down`. These process-only placeholders
# are used solely to select the existing project; they are never sent to a
# container because this command does not create or start anything.
foreach ($name in @(
    'TASKFLOW_ACCEPTANCE_DB_ROOT_PASSWORD', 'TASKFLOW_ACCEPTANCE_DB_USERNAME',
    'TASKFLOW_ACCEPTANCE_DB_PASSWORD', 'TASKFLOW_ACCEPTANCE_RABBITMQ_USERNAME',
    'TASKFLOW_ACCEPTANCE_RABBITMQ_PASSWORD', 'TASKFLOW_ACCEPTANCE_MINIO_ROOT_USER',
    'TASKFLOW_ACCEPTANCE_MINIO_ROOT_PASSWORD', 'TASKFLOW_ACCEPTANCE_JWT_SECRET',
    'TASKFLOW_ACCEPTANCE_ADMIN_USERNAME', 'TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD',
    'TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD'
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        [Environment]::SetEnvironmentVariable($name, 'down-only-placeholder', 'Process')
    }
}

# Intentionally omit -v: acceptance volumes are persistent and are never
# deleted by the lifecycle helper.
docker compose -f $composeFile down
if ($LASTEXITCODE -ne 0) { throw 'Acceptance Compose shutdown failed.' }
Write-Output 'Acceptance containers stopped; volumes were preserved.'
