#!/usr/bin/env bash
set -euo pipefail

ENV="dev"
K8S_DIR="$(dirname "$0")/k8s"

usage() {
  echo "Usage: $0 [--env dev|test|prod]"
  echo "  --env dev   Build locally and deploy to metro-dev namespace (default)"
  echo "  --env test  Pull ghcr.io/kejhy93/metro-timetable:main and deploy to metro-test namespace"
  echo "  --env prod  Pull ghcr.io/kejhy93/metro-timetable:latest and deploy to metro-prod namespace"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENV="$2"; shift 2 ;;
    *) usage ;;
  esac
done

case "$ENV" in
  dev)
    LOCAL_IMAGE="localhost/metro-timetable:local"
    echo "==> Building JAR..."
    mvn -B clean package -DskipTests

    echo "==> Building Docker image: $LOCAL_IMAGE..."
    podman build -t "$LOCAL_IMAGE" .

    echo "==> Loading image into minikube..."
    podman save "$LOCAL_IMAGE" | minikube image load --overwrite=true -
    ;;
  test)
    REMOTE_IMAGE="ghcr.io/kejhy93/metro-timetable:main"
    echo "==> Loading remote image into minikube: $REMOTE_IMAGE..."
    minikube image load "$REMOTE_IMAGE"
    ;;
  prod)
    REMOTE_IMAGE="ghcr.io/kejhy93/metro-timetable:latest"
    echo "==> Loading remote image into minikube: $REMOTE_IMAGE..."
    minikube image load "$REMOTE_IMAGE"
    ;;
  *)
    echo "Unknown environment: $ENV"
    usage
    ;;
esac

echo "==> Applying Kubernetes manifests for env=$ENV..."
kubectl apply -k "$K8S_DIR/overlays/$ENV"

echo "==> Waiting for rollout in namespace metro-$ENV..."
kubectl rollout status deployment/metro-timetable -n "metro-$ENV" --timeout=120s

if [[ "$ENV" == "dev" || "$ENV" == "test" || "$ENV" == "prod" ]]; then
  if kill -0 "$(cat /tmp/minikube-tunnel.pid 2>/dev/null)" 2>/dev/null; then
    echo "==> minikube tunnel already running (PID $(cat /tmp/minikube-tunnel.pid))"
  else
    echo "==> Starting minikube tunnel in background (PID will be saved to /tmp/minikube-tunnel.pid)..."
    minikube tunnel > /tmp/minikube-tunnel.log 2>&1 &
    echo $! > /tmp/minikube-tunnel.pid
    echo "    Tunnel log: /tmp/minikube-tunnel.log"
    echo "    To stop: kill \$(cat /tmp/minikube-tunnel.pid)"
  fi
fi

echo ""
echo "==> Done. Access the service with:"
echo "    minikube service metro-timetable -n metro-$ENV --url"
