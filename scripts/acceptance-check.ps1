[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ($env:TASKFLOW_ACCEPTANCE_BASE_URL) { $env:TASKFLOW_ACCEPTANCE_BASE_URL } else { 'http://127.0.0.1:15173' })
)

$ErrorActionPreference = 'Stop'
foreach ($name in @('TASKFLOW_ACCEPTANCE_ADMIN_USERNAME', 'TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Missing required acceptance environment variable: $name"
    }
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $params = @{ Method = $Method; Uri = $Uri; Headers = $Headers; ContentType = 'application/json'; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Compress) }
    $response = Invoke-WebRequest @params
    $body = $null
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) { $body = $response.Content | ConvertFrom-Json }
    return @{ Status = [int]$response.StatusCode; Body = $body }
}

$health = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/health"
if ($health.Status -ne 200) { throw "Acceptance backend health check failed with HTTP $($health.Status)." }

$login = Invoke-JsonRequest -Method Post -Uri "$BaseUrl/api/auth/login" -Body @{
    login = $env:TASKFLOW_ACCEPTANCE_ADMIN_USERNAME
    password = $env:TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD
}
if ($login.Status -ne 200 -or [string]::IsNullOrWhiteSpace($login.Body.data.accessToken)) {
    throw "Acceptance admin login failed with HTTP $($login.Status)."
}
$token = $login.Body.data.accessToken
$headers = @{ Authorization = "Bearer $token" }

$me = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/auth/me" -Headers $headers
if ($me.Status -ne 200) { throw "Acceptance /api/auth/me failed with HTTP $($me.Status)." }

$tasks = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/tasks?page=1&size=20" -Headers $headers
if ($tasks.Status -ne 200) { throw "Acceptance task list failed with HTTP $($tasks.Status)." }

$logout = Invoke-JsonRequest -Method Post -Uri "$BaseUrl/api/auth/logout" -Headers $headers
if ($logout.Status -ne 200) { throw "Acceptance logout failed with HTTP $($logout.Status)." }

$oldSession = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/auth/me" -Headers $headers
if ($oldSession.Status -ne 401) { throw "Revoked acceptance session returned HTTP $($oldSession.Status), expected 401." }

Write-Output 'Acceptance smoke passed: health, login, auth/me, task list, logout, revoked-session 401.'
