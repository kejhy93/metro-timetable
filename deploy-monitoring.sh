#!/usr/bin/env bash
set -euo pipefail

K8S_DIR="$(dirname "$0")/k8s"

# k3s stores its kubeconfig outside the default location
if [[ -f /etc/rancher/k3s/k3s.yaml && -z "${KUBECONFIG:-}" ]]; then
  export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
fi

echo "==> Adding prometheus-community helm repo..."
if ! helm repo list | grep -q "^prometheus-community\b"; then
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
fi
helm repo update

echo "==> Installing kube-prometheus-stack..."
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin

echo "==> Applying ServiceMonitor..."
kubectl apply -f "$K8S_DIR/base/servicemonitor.yaml"

echo "==> Waiting for monitoring pods to be ready..."
kubectl --namespace monitoring wait --for=condition=ready pod \
  -l "release=prometheus" \
  --timeout=120s

echo ""
echo "==> Done. Access the UIs with:"
echo "    Prometheus: kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090"
echo "    Grafana:    kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80"
echo "    Grafana login: admin / admin"
