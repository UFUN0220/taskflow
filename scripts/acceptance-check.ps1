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

$webSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [Microsoft.PowerShell.Commands.WebRequestSession]$WebSession,
        [int[]]$ExpectedStatusCodes = @()
    )
    $params = @{ Method = $Method; Uri = $Uri; Headers = $Headers; ContentType = 'application/json'; UseBasicParsing = $true; WebSession = $WebSession }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Compress) }
    if ((Get-Command Invoke-WebRequest).Parameters.ContainsKey('SkipHttpErrorCheck')) {
        $params.SkipHttpErrorCheck = $true
    }
    try {
        $response = Invoke-WebRequest @params
        $content = $response.Content
        $responseHeaders = $response.Headers
        $status = [int]$response.StatusCode
    } catch {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) { throw }

        # Windows PowerShell 5.1 exposes HttpWebResponse, while PowerShell 7
        # can expose HttpResponseMessage. Keep the status and body instead of
        # assuming that both types implement GetResponseStream().
        $responseTypeName = $errorResponse.GetType().FullName
        if ($responseTypeName -eq 'System.Net.Http.HttpResponseMessage') {
            try {
                $content = $errorResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            } catch {
                # PowerShell 7 may dispose this response before the catch block;
                # the status code remains authoritative for the assertion.
                $content = ''
            }
        } elseif ($errorResponse -is [System.Net.HttpWebResponse]) {
            $reader = New-Object System.IO.StreamReader($errorResponse.GetResponseStream())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        } elseif ($errorResponse.Content -and $errorResponse.Content.PSObject.Methods.Name -contains 'ReadAsStringAsync') {
            $content = $errorResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        } else {
            $content = ''
        }
        $responseHeaders = $errorResponse.Headers
        $status = [int]$errorResponse.StatusCode
    }
    $body = $null
    if (-not [string]::IsNullOrWhiteSpace($content)) { $body = $content | ConvertFrom-Json }
    $classification = if ($ExpectedStatusCodes -contains $status) {
        'EXPECTED_HTTP_FAILURE'
    } elseif ($status -ge 400) {
        'UNEXPECTED_HTTP_FAILURE'
    } else {
        'NONE'
    }
    return @{ Status = $status; Body = $body; Headers = $responseHeaders; Classification = $classification }
}

$health = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/health" -WebSession $webSession
if ($health.Status -ne 200) { throw "Acceptance backend health check failed with HTTP $($health.Status)." }

$login = Invoke-JsonRequest -Method Post -Uri "$BaseUrl/api/auth/login" -WebSession $webSession -Body @{
    login = $env:TASKFLOW_ACCEPTANCE_ADMIN_USERNAME
    password = $env:TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD
}
if ($login.Status -ne 200 -or [string]::IsNullOrWhiteSpace($login.Body.data.accessToken)) {
    throw "Acceptance admin login failed with HTTP $($login.Status)."
}
$accessCookie = [string]$login.Headers['Set-Cookie']
if ($accessCookie -notmatch '(?i)HttpOnly' -or $accessCookie -notmatch '(?i)SameSite=Lax') {
    throw 'Acceptance login did not set the expected HttpOnly/SameSite access cookie.'
}

$me = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/auth/me" -WebSession $webSession
if ($me.Status -ne 200) { throw "Acceptance /api/auth/me failed with HTTP $($me.Status)." }

$tasks = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/tasks?page=1&size=20" -WebSession $webSession
if ($tasks.Status -ne 200) { throw "Acceptance task list failed with HTTP $($tasks.Status)." }

$csrf = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/auth/csrf" -WebSession $webSession
if ($csrf.Status -ne 200 -or [string]::IsNullOrWhiteSpace($csrf.Body.data)) {
    throw "Acceptance CSRF bootstrap failed with HTTP $($csrf.Status)."
}

$logout = Invoke-JsonRequest -Method Post -Uri "$BaseUrl/api/auth/logout" -WebSession $webSession -Headers @{ 'X-XSRF-TOKEN' = [string]$csrf.Body.data }
if ($logout.Status -ne 200) { throw "Acceptance logout failed with HTTP $($logout.Status)." }

$oldSession = Invoke-JsonRequest -Method Get -Uri "$BaseUrl/api/auth/me" -WebSession $webSession -ExpectedStatusCodes @(401)
if ($oldSession.Status -ne 401) { throw "Revoked acceptance session returned HTTP $($oldSession.Status), expected 401." }

Write-Output 'Acceptance smoke passed: health, CSRF bootstrap, HttpOnly cookie login, cookie auth/me, task list, CSRF logout, revoked-session 401.'
