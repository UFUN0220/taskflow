[CmdletBinding()]
param(
    [ValidateSet('docker-desktop', 'kind', 'minikube')]
    [string]$Runtime = 'docker-desktop',
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $projectRoot 'k8s'

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw 'kubectl was not found in PATH.'
}

if (-not (Test-Path -LiteralPath (Join-Path $manifestPath 'kustomization.yaml'))) {
    throw "Kubernetes manifests were not found: $manifestPath"
}

if ($Runtime -eq 'kind') {
    if (-not (Get-Command kind -ErrorAction SilentlyContinue)) {
        throw 'kind was not found in PATH. Install Kind or use -Runtime docker-desktop.'
    }
    kind load docker-image taskflow-platform-backend:latest
    kind load docker-image taskflow-platform-frontend:latest
}

if ($Runtime -eq 'minikube') {
    if (-not (Get-Command minikube -ErrorAction SilentlyContinue)) {
        throw 'minikube was not found in PATH. Install Minikube or use -Runtime docker-desktop.'
    }
    minikube image load taskflow-platform-backend:latest
    minikube image load taskflow-platform-frontend:latest
}

Write-Output 'Rendered Kubernetes resources:'
kubectl kustomize $manifestPath

if ($Apply) {
    Write-Output 'Applying Kubernetes resources...'
    kubectl apply -k $manifestPath
    kubectl rollout status deployment/backend -n taskflow --timeout=180s
    kubectl rollout status deployment/frontend -n taskflow --timeout=180s
    kubectl get pods,svc -n taskflow -o wide
}
else {
    Write-Output 'Dry render only. Re-run with -Apply after configuring k8s/secret.yaml and selecting a working cluster context.'
}
