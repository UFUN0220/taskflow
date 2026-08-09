<#!
.SYNOPSIS
    TaskFlow 本地非破坏性故障注入与恢复证据采集。

.DESCRIPTION
    只允许对当前 TaskFlow Compose 容器执行 stop/start，禁止 compose down、rm、volume
    删除和数据库清空。脚本默认只做计划检查；必须显式传入 -Execute 才会注入故障。
    结果只保存状态、计数和健康检查，不保存密码、JWT 或 Cookie。
#>
param(
    [ValidateSet("redis", "rabbitmq", "minio", "mysql", "backend", "websocket", "all")]
    [string]$Scenario = "all",
    [string]$Output = "docs/fault-injection-2026-08-09.json",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AccessToken = $env:TASKFLOW_FAULT_ACCESS_TOKEN,
    [string]$MysqlUser = $(if ($env:TASKFLOW_FAULT_DB_USER) { $env:TASKFLOW_FAULT_DB_USER } else { "taskflow" }),
    [string]$MysqlPassword = $env:TASKFLOW_FAULT_DB_PASSWORD,
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$containers = [ordered]@{
    redis = "taskflow-platform-redis-1"
    rabbitmq = "taskflow-platform-rabbitmq-1"
    minio = "taskflow-platform-minio-1"
    mysql = "taskflow-platform-mysql-1"
    backend = "taskflow-platform-backend-1"
}

function Invoke-DockerChecked {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed: $($output -join ' ')"
    }
    return ($output -join "`n").Trim()
}

function Get-ContainerState {
    param([Parameter(Mandatory = $true)][string]$Name)
    $raw = Invoke-DockerChecked @("inspect", $Name)
    $item = @($raw | ConvertFrom-Json)[0]
    $health = $null
    if ($item.State.Health) { $health = $item.State.Health.Status }
    return [ordered]@{
        status = $item.State.Status
        health = $health
        running = [bool]$item.State.Running
    }
}

function Assert-AllowedContainer {
    param([Parameter(Mandatory = $true)][string]$Name)
    if ($containers.Values -notcontains $Name) {
        throw "拒绝操作非 TaskFlow 容器: $Name"
    }
    Get-ContainerState -Name $Name | Out-Null
}

function Wait-Healthy {
    param([Parameter(Mandatory = $true)][string]$Name, [int]$TimeoutSeconds = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = Get-ContainerState -Name $Name
        if ($state.status -eq "running" -and ($null -eq $state.health -or $state.health -eq "healthy")) {
            return $state
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "等待容器恢复健康超时: $Name"
}

function Get-HttpStatus {
    param([Parameter(Mandatory = $true)][string]$Uri)
    try {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        return $null
    }
}

function Get-AuthMeStatus {
    if ([string]::IsNullOrWhiteSpace($AccessToken)) { return "not_collected_no_token" }
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/auth/me" -Headers @{ Authorization = "Bearer $AccessToken" } -UseBasicParsing -TimeoutSec 5
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        return $null
    }
}

function Invoke-MySqlQuery {
    param([Parameter(Mandatory = $true)][string]$Query)
    if ([string]::IsNullOrWhiteSpace($MysqlPassword)) { return $null }
    $container = $containers.mysql
    try {
        return Invoke-DockerChecked @("exec", "-e", ("MYSQL_PWD=" + $MysqlPassword), $container,
            "mysql", "--protocol=TCP", ("-u" + $MysqlUser), "-N", "-B", "-D", "taskflow", "-e", $Query)
    } catch {
        return $null
    }
}

function Get-DatabaseSnapshot {
    $raw = Invoke-MySqlQuery -Query @"
SELECT CONCAT('flyway=', COALESCE((SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1), 'none'));
SELECT CONCAT('tasks=', (SELECT COUNT(*) FROM task WHERE deleted = 0));
SELECT CONCAT('notifications=', (SELECT COUNT(*) FROM notification WHERE deleted = 0));
SELECT CONCAT('attachments=', (SELECT COUNT(*) FROM task_attachment WHERE deleted = 0));
SELECT CONCAT('attachment_states=', COALESCE((SELECT GROUP_CONCAT(CONCAT(status, ':', amount) ORDER BY status SEPARATOR ',' ) FROM (SELECT status, COUNT(*) amount FROM task_attachment WHERE deleted = 0 GROUP BY status) states), 'none'));
"@
    if ([string]::IsNullOrWhiteSpace($raw)) { return [ordered]@{ status = "not_collected" } }
    $result = [ordered]@{ status = "ok" }
    foreach ($line in ($raw -split "`r?`n")) {
        if ($line -match "^(flyway|tasks|notifications|attachments|attachment_states)=(.*)$") {
            $result[$Matches[1]] = $Matches[2]
        }
    }
    return $result
}

function Get-RedisSnapshot {
    try {
        $ping = Invoke-DockerChecked @("exec", $containers.redis, "redis-cli", "PING")
        $zset = Invoke-DockerChecked @("exec", $containers.redis, "redis-cli", "ZCARD", "taskflow:reminders:due")
        return [ordered]@{ status = "ok"; ping = $ping; reminderZsetCardinality = $zset }
    } catch { return [ordered]@{ status = "unavailable" } }
}

function Get-RabbitSnapshot {
    try {
        $queues = Invoke-DockerChecked @("exec", $containers.rabbitmq, "rabbitmqctl", "list_queues", "name", "messages_ready", "messages_unacknowledged", "--formatter", "json")
        return [ordered]@{ status = "ok"; queues = $queues }
    } catch { return [ordered]@{ status = "unavailable" } }
}

function Get-MinioSnapshot {
    return [ordered]@{ status = "http"; live = (Get-HttpStatus -Uri "http://localhost:9000/minio/health/live") }
}

function Get-Snapshot {
    return [ordered]@{
        capturedAt = (Get-Date).ToUniversalTime().ToString("o")
        appHealth = Get-HttpStatus -Uri "$BaseUrl/api/health"
        authMe = Get-AuthMeStatus
        backend = Get-ContainerState -Name $containers.backend
        mysql = Get-DatabaseSnapshot
        redis = Get-RedisSnapshot
        rabbitmq = Get-RabbitSnapshot
        minio = Get-MinioSnapshot
    }
}

function Compare-Database {
    param($Before, $After)
    if ($Before.status -ne "ok" -or $After.status -ne "ok") { return "not_comparable_database_snapshot_missing" }
    $keys = @("flyway", "tasks", "notifications", "attachments", "attachment_states")
    foreach ($key in $keys) {
        if ($Before[$key] -ne $After[$key]) { return "changed_$key" }
    }
    return "unchanged_for_observed_counts"
}

function Run-Fault {
    param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][string]$Label)
    Assert-AllowedContainer -Name $Name
    $before = Get-Snapshot
    if (-not $Execute) {
        return [ordered]@{ scenario = $Label; status = "planned_requires_-Execute"; before = $before }
    }
    Invoke-DockerChecked @("stop", $Name) | Out-Null
    Start-Sleep -Seconds 3
    $during = Get-Snapshot
    Invoke-DockerChecked @("start", $Name) | Out-Null
    $restored = Wait-Healthy -Name $Name
    Start-Sleep -Seconds 5
    $after = Get-Snapshot
    return [ordered]@{
        scenario = $Label
        status = "executed"
        target = $Name
        before = $before
        during = $during
        after = $after
        restoredTarget = $restored
        databaseConsistency = Compare-Database -Before $before.mysql -After $after.mysql
        volumeOperation = "none"
        productionBoundary = "single local container restart only; not HA or failover evidence"
    }
}

$selected = if ($Scenario -eq "all") { @("redis", "rabbitmq", "minio", "mysql", "backend", "websocket") } else { @($Scenario) }
$results = @()
foreach ($item in $selected) {
    if ($item -eq "websocket") {
        $results += Run-Fault -Name $containers.backend -Label "backend_restart_for_websocket_reconnect"
    } elseif ($item -eq "backend") {
        $results += Run-Fault -Name $containers.backend -Label "backend_restart"
    } else {
        $results += Run-Fault -Name $containers[$item] -Label "${item}_restart"
    }
}

$result = [ordered]@{
    capturedAt = (Get-Date).ToUniversalTime().ToString("o")
    project = "TaskFlow Platform"
    execute = [bool]$Execute
    safety = @(
        "Only exact taskflow-platform-* container names were allowed",
        "Only docker stop/start were used for injection",
        "No compose down, docker rm, volume deletion or database cleanup was performed"
    )
    scenarios = $results
    businessProbeBoundary = if ([string]::IsNullOrWhiteSpace($AccessToken)) { "API probes not collected; provide TASKFLOW_FAULT_ACCESS_TOKEN for authenticated checks" } else { "Authenticated /api/auth/me status collected; task mutation and browser WebSocket MESSAGE require separate explicit probe" }
}

$target = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Output))
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
$result | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $target -Encoding UTF8
Write-Output "故障演练结果已写入 $target"
