param(
    [switch]$Rebuild,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Test-Path -LiteralPath ".env")) {
    Copy-Item -LiteralPath ".env.example" -Destination ".env"
    Write-Output "已从 .env.example 创建 .env；部署前请修改本地密码和 JWT Secret。"
}

if ($Rebuild) {
    docker compose up -d --build
} else {
    docker compose up -d
}
if ($LASTEXITCODE -ne 0) { throw "docker compose up 失败" }

$services = @("mysql", "redis", "rabbitmq", "minio", "backend", "frontend")
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    $healthy = $true
    foreach ($service in $services) {
        $containerId = (docker compose ps -q $service).Trim()
        if (-not $containerId) {
            $healthy = $false
            continue
        }
        $status = (docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null).Trim()
        if ($status -ne "healthy") { $healthy = $false }
    }
    if ($healthy) { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

if (-not $healthy) {
    docker compose ps
    throw "等待所有服务健康超时；请执行 docker compose logs backend frontend 检查启动日志。"
}

docker compose ps
$frontendAddress = (docker compose port frontend 80).Trim()
$backendAddress = (docker compose port backend 8080).Trim()
Write-Output "全容器模式已启动：前端 $frontendAddress，后端健康检查 $backendAddress/actuator/health"
