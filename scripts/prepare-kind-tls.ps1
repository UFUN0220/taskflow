param(
    [string]$Namespace = "taskflow",
    [string]$SecretName = "taskflow-tls",
    [string]$HostName = "taskflow.local",
    [string]$OutputDirectory = "runtime-secrets/kind-tls",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$openssl = (Get-Command openssl -ErrorAction SilentlyContinue).Source
if (-not $openssl) { throw "openssl was not found in PATH" }

$outputPath = Join-Path $projectRoot $OutputDirectory
$certificatePath = Join-Path $outputPath "$HostName.crt"
$keyPath = Join-Path $outputPath "$HostName.key"
if (((Test-Path -LiteralPath $certificatePath) -or (Test-Path -LiteralPath $keyPath)) -and -not $Force) {
    throw "本地证书已存在；如需重新生成请显式传入 -Force。"
}

New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$opensslConfig = Join-Path (Split-Path -Parent (Split-Path -Parent $openssl)) "ssl\openssl.cnf"
$opensslArgs = @(
    "req", "-x509", "-nodes", "-newkey", "rsa:2048", "-days", "7",
    "-keyout", $keyPath, "-out", $certificatePath,
    "-subj", "/CN=$HostName",
    "-addext", "subjectAltName=DNS:$HostName,DNS:localhost,IP:127.0.0.1"
)
if (Test-Path -LiteralPath $opensslConfig) { $opensslArgs += @("-config", $opensslConfig) }
& $openssl @opensslArgs | Out-Null
if ($LASTEXITCODE -ne 0) { throw "生成本地开发证书失败" }

$kubectl = (Get-Command kubectl -ErrorAction SilentlyContinue).Source
if (-not $kubectl -and (Test-Path -LiteralPath "F:\newinstall\kubectl.exe")) {
    $kubectl = "F:\newinstall\kubectl.exe"
}
if (-not $kubectl) { throw "kubectl was not found in PATH or F:\newinstall" }

& $kubectl create namespace $Namespace --dry-run=client -o yaml | & $kubectl apply -f - | Out-Null
& $kubectl create secret tls $SecretName -n $Namespace --cert=$certificatePath --key=$keyPath --dry-run=client -o yaml | & $kubectl apply -f - | Out-Null
if ($LASTEXITCODE -ne 0) { throw "创建 TLS Secret 失败" }

Write-Output "已创建本地 TLS Secret $SecretName/$Namespace。证书仅用于 $HostName，私钥位于被 .gitignore 忽略的 runtime-secrets 目录，未写入仓库。"
