#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 --email <email>"
  echo "  --email  Your email address for Let's Encrypt notifications"
  exit 1
}

EMAIL=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --email) EMAIL="$2"; shift 2 ;;
    *) usage ;;
  esac
done

if [[ -z "$EMAIL" ]]; then
  echo "Error: --email is required"
  usage
fi

echo "==> Installing cert-manager..."
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml

echo "==> Waiting for cert-manager to be ready..."
kubectl wait --namespace cert-manager \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo "==> Creating ClusterIssuer (letsencrypt-prod)..."
kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ${EMAIL}
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            ingressClassName: traefik
EOF

echo "==> Done. Verify with:"
echo "    kubectl get clusterissuer letsencrypt-prod"
echo "    kubectl describe clusterissuer letsencrypt-prod"
