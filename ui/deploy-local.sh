#!/usr/bin/env bash
set -euo pipefail

ENV="dev"
SCRIPT_DIR="$(dirname "$0")"
K8S_DIR="$SCRIPT_DIR/k8s"

usage() {
  echo "Usage: $0 [--env dev|test|prod]"
  echo "  --env dev   Build locally and deploy to metro-dev namespace (default)"
  echo "  --env test  Pull ghcr.io/kejhy93/metro-timetable-ui:main and deploy to metro-test namespace"
  echo "  --env prod  Pull ghcr.io/kejhy93/metro-timetable-ui:latest and deploy to metro-prod namespace"
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
    LOCAL_IMAGE="localhost/metro-timetable-ui:local"
    echo "==> Building wasmJs distribution..."
    (cd "$SCRIPT_DIR" && ./gradlew :composeApp:wasmJsBrowserDistribution)

    echo "==> Building Docker image: $LOCAL_IMAGE..."
    podman build -t "$LOCAL_IMAGE" "$SCRIPT_DIR"

    echo "==> Loading image into minikube..."
    podman save "$LOCAL_IMAGE" | minikube image load --overwrite=true -
    ;;
  test)
    REMOTE_IMAGE="ghcr.io/kejhy93/metro-timetable-ui:main"
    echo "==> Loading remote image into minikube: $REMOTE_IMAGE..."
    minikube image load "$REMOTE_IMAGE"
    ;;
  prod)
    REMOTE_IMAGE="ghcr.io/kejhy93/metro-timetable-ui:latest"
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
kubectl rollout status deployment/metro-timetable-ui -n "metro-$ENV" --timeout=120s

if [[ "$ENV" == "dev" ]]; then
  if kill -0 "$(cat /tmp/minikube-svc-tunnel.pid 2>/dev/null)" 2>/dev/null; then
    echo "==> minikube service tunnel already running (PID $(cat /tmp/minikube-svc-tunnel.pid))"
    SVC_URL=$(cat /tmp/minikube-svc-tunnel.url 2>/dev/null)
  else
    echo "==> Starting minikube service tunnel in background..."
    SVC_URL_FILE=$(mktemp)
    minikube service metro-timetable-ui -n metro-dev --url > "$SVC_URL_FILE" 2>/dev/null &
    echo $! > /tmp/minikube-svc-tunnel.pid
    sleep 3
    SVC_URL=$(head -1 "$SVC_URL_FILE")
    echo "$SVC_URL" > /tmp/minikube-svc-tunnel.url
    rm -f "$SVC_URL_FILE"
    echo "    To stop: kill \$(cat /tmp/minikube-svc-tunnel.pid)"
  fi

  echo ""
  echo "==> Open in browser:"
  echo "    ${SVC_URL}/metro"
fi
