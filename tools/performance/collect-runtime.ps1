param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AccessToken = $env:TASKFLOW_PERF_ACCESS_TOKEN,
    [int]$AppPid = 0,
    [string]$RedisService = "redis",
    [string]$RedisContainer = "taskflow-platform-redis-1",
    [string]$Output = "docs/performance-runtime.json"
)

$ErrorActionPreference = "Stop"
$headers = @{}
if ($AccessToken) {
    $headers["Authorization"] = "Bearer $AccessToken"
}

$metricNames = @(
    "http.server.requests",
    "hikaricp.connections.active",
    "hikaricp.connections.idle",
    "hikaricp.connections.pending",
    "hikaricp.connections.max",
    "hikaricp.connections.usage",
    "executor.active",
    "executor.queued",
    "jvm.gc.pause",
    "jvm.memory.used",
    "rabbitmq.listener",
    "rabbitmq.connections",
    "rabbitmq.channels",
    "rabbitmq.published",
    "rabbitmq.consumed"
)

$actuator = [ordered]@{}
foreach ($metricName in $metricNames) {
    $encodedName = [System.Uri]::EscapeDataString($metricName)
    try {
        $actuator[$metricName] = Invoke-RestMethod -Uri "$BaseUrl/actuator/metrics/$encodedName" -Headers $headers -Method Get
    } catch {
        $actuator[$metricName] = [ordered]@{ unavailable = $_.Exception.Message }
    }
}

$jvm = [ordered]@{}
if ($AppPid -gt 0) {
    try {
        $jvm["vmFlags"] = (& jcmd $AppPid VM.flags 2>&1 | Out-String).Trim()
        $jvm["gcHeap"] = (& jcmd $AppPid GC.heap_info 2>&1 | Out-String).Trim()
        $jvm["threads"] = (& jcmd $AppPid Thread.print 2>&1 | Out-String).Trim()
    } catch {
        $jvm["unavailable"] = $_.Exception.Message
    }
} else {
    $jvm["unavailable"] = "未提供 -AppPid，未执行 jcmd 采集"
}

$redis = $null
try {
    $redis = (& docker exec $RedisContainer redis-cli INFO stats 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "docker exec failed for $RedisContainer"
    }
} catch {
    try {
        $redis = (& docker compose exec -T $RedisService redis-cli INFO stats 2>&1 | Out-String).Trim()
    } catch {
        $redis = "Redis 采集失败: $($_.Exception.Message)"
    }
}

$result = [ordered]@{
    capturedAt = (Get-Date).ToUniversalTime().ToString("o")
    baseUrl = $BaseUrl
    appPid = $AppPid
    actuator = $actuator
    jvm = $jvm
    redisInfoStats = $redis
}

$target = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Output))
$parent = Split-Path -Parent $target
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $target -Encoding UTF8
Write-Output "运行时观测结果已写入 $target"
