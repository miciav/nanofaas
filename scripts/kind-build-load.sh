#!/usr/bin/env bash
set -euo pipefail

VM_NAME=${VM_NAME:-mcfaas-kind}
KIND_CLUSTER=${KIND_CLUSTER:-mcfaas}
REMOTE_DIR=${REMOTE_DIR:-/home/ubuntu/mcFaas}

if ! command -v multipass >/dev/null 2>&1; then
  echo "multipass not found. Install it first." >&2
  exit 1
fi

if ! multipass list | awk '{print $1}' | grep -q "^${VM_NAME}$"; then
  echo "VM ${VM_NAME} not found. Run scripts/setup-multipass-kind.sh first." >&2
  exit 1
fi

multipass exec "$VM_NAME" -- bash -lc "rm -rf ${REMOTE_DIR}"

multipass transfer --recursive . "$VM_NAME":"$REMOTE_DIR"

multipass exec "$VM_NAME" -- bash -lc "cd ${REMOTE_DIR} && ./gradlew :control-plane:bootJar :function-runtime:bootJar"

multipass exec "$VM_NAME" -- bash -lc "cd ${REMOTE_DIR} && docker build -t mcfaas/control-plane:0.1.0 control-plane/"
multipass exec "$VM_NAME" -- bash -lc "cd ${REMOTE_DIR} && docker build -t mcfaas/function-runtime:0.1.0 function-runtime/"

multipass exec "$VM_NAME" -- bash -lc "kind load docker-image mcfaas/control-plane:0.1.0 --name ${KIND_CLUSTER}"
multipass exec "$VM_NAME" -- bash -lc "kind load docker-image mcfaas/function-runtime:0.1.0 --name ${KIND_CLUSTER}"

