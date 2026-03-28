#!/usr/bin/env bash
set -euo pipefail

K8S_DIR="$(dirname "$0")/k8s"
ENV="prod"

usage() {
  echo "Usage: $0 [--env local|prod]"
  echo "  --env local  Deploy to minikube; access Grafana via port-forward (default: prod)"
  echo "  --env prod   Deploy to k3s with TLS ingress at https://hejnaluk.dev/grafana"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENV="$2"; shift 2 ;;
    *) usage ;;
  esac
done

case "$ENV" in
  local|prod) ;;
  *) echo "Unknown environment: $ENV"; usage ;;
esac

# k3s stores its kubeconfig outside the default location
if [[ "$ENV" == "prod" && -f /etc/rancher/k3s/k3s.yaml && -z "${KUBECONFIG:-}" ]]; then
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
  --set grafana.adminPassword=admin \
  --set "grafana.grafana\.ini.server.root_url=https://hejnaluk.dev/grafana" \
  --set "grafana.grafana\.ini.server.serve_from_sub_path=true"

echo "==> Applying ServiceMonitor..."
kubectl apply -f "$K8S_DIR/base/servicemonitor.yaml"

echo "==> Waiting for monitoring pods to be ready..."
kubectl --namespace monitoring wait --for=condition=ready pod \
  -l "release=prometheus" \
  --timeout=120s

if [[ "$ENV" == "prod" ]]; then
  echo "==> Applying Grafana Ingress..."
  kubectl apply -f "$K8S_DIR/monitoring/grafana-ingress.yaml"
fi

echo ""
echo "==> Done. Access the UIs with:"
echo "    Prometheus: kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090"
if [[ "$ENV" == "prod" ]]; then
  echo "    Grafana:    https://hejnaluk.dev/grafana"
else
  echo "    Grafana:    ./port-forward-grafana.sh  (then open http://localhost:3000)"
fi
echo "    Grafana login: admin / admin"
