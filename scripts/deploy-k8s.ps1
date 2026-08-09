[CmdletBinding()]
param(
    [ValidateSet('docker-desktop', 'kind', 'minikube')]
    [string]$Runtime = 'docker-desktop',
    [string]$KindClusterName = 'dev',
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $projectRoot 'k8s'
$toolRoot = 'F:\newinstall'
$kubectlCommand = (Get-Command kubectl -ErrorAction SilentlyContinue).Source
if (-not $kubectlCommand -and (Test-Path -LiteralPath (Join-Path $toolRoot 'kubectl.exe'))) {
    $kubectlCommand = Join-Path $toolRoot 'kubectl.exe'
}
$kindCommand = (Get-Command kind -ErrorAction SilentlyContinue).Source
if (-not $kindCommand -and (Test-Path -LiteralPath (Join-Path $toolRoot 'kind.exe'))) {
    $kindCommand = Join-Path $toolRoot 'kind.exe'
}

if (-not $kubectlCommand) {
    throw 'kubectl was not found in PATH.'
}

if (-not (Test-Path -LiteralPath (Join-Path $manifestPath 'kustomization.yaml'))) {
    throw "Kubernetes manifests were not found: $manifestPath"
}

if ($Runtime -eq 'kind') {
    if (-not $kindCommand) {
        throw 'kind was not found in PATH. Install Kind or use -Runtime docker-desktop.'
    }
    & $kindCommand load docker-image taskflow-platform-backend:latest --name $KindClusterName
    & $kindCommand load docker-image taskflow-platform-frontend:latest --name $KindClusterName
}

if ($Runtime -eq 'minikube') {
    if (-not (Get-Command minikube -ErrorAction SilentlyContinue)) {
        throw 'minikube was not found in PATH. Install Minikube or use -Runtime docker-desktop.'
    }
    minikube image load taskflow-platform-backend:latest
    minikube image load taskflow-platform-frontend:latest
}

Write-Output 'Rendered Kubernetes resources:'
& $kubectlCommand kustomize $manifestPath

if ($Apply) {
    Write-Output 'Applying Kubernetes resources...'
    & $kubectlCommand apply -k $manifestPath
    & $kubectlCommand rollout status deployment/backend -n taskflow --timeout=180s
    & $kubectlCommand rollout status deployment/frontend -n taskflow --timeout=180s
    & $kubectlCommand get pods,svc -n taskflow -o wide
}
else {
    Write-Output 'Dry render only. Re-run with -Apply after configuring k8s/secret.yaml and selecting a working cluster context.'
}
