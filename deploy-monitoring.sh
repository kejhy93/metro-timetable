#!/usr/bin/env bash
set -euo pipefail

K8S_DIR="$(dirname "$0")/k8s"

echo "==> Adding prometheus-community helm repo..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

echo "==> Installing kube-prometheus-stack..."
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin

echo "==> Applying ServiceMonitor..."
kubectl apply -f "$K8S_DIR/servicemonitor.yaml"

echo "==> Waiting for monitoring pods to be ready..."
kubectl --namespace monitoring wait --for=condition=ready pod \
  -l "release=prometheus" \
  --timeout=120s

echo ""
echo "==> Done. Access the UIs with:"
echo "    Prometheus: kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090"
echo "    Grafana:    kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80"
echo "    Grafana login: admin / admin"
