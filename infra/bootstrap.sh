#!/usr/bin/env bash
set -euo pipefail

CERT_MANAGER_VERSION="v1.16.2"
REPO_DIR="$HOME/fitconnect"

if ! command -v k3s >/dev/null 2>&1; then
  curl -sfL https://get.k3s.io | sh -
fi

mkdir -p "$HOME/.kube"
sudo cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
export KUBECONFIG="$HOME/.kube/config"
grep -q 'KUBECONFIG=' "$HOME/.bashrc" || echo 'export KUBECONFIG=$HOME/.kube/config' >> "$HOME/.bashrc"

if ! command -v helm >/dev/null 2>&1; then
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
fi

helm repo add jetstack https://charts.jetstack.io >/dev/null 2>&1 || true
helm repo update >/dev/null
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version "${CERT_MANAGER_VERSION}" \
  --set crds.enabled=true \
  --wait

kubectl apply -f "${REPO_DIR}/k8s/cluster-issuer.yaml"
