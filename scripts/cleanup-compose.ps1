param(
    [switch]$RemoveVolumes,
    [switch]$ConfirmDataLoss
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if ($RemoveVolumes -and -not $ConfirmDataLoss) {
    throw "删除数据库、Redis、RabbitMQ、MinIO 卷会丢失本地数据；如确需删除，请同时传入 -ConfirmDataLoss。"
}

if ($RemoveVolumes) {
    docker compose down --volumes
} else {
    docker compose down
}
if ($LASTEXITCODE -ne 0) { throw "docker compose down 失败" }
