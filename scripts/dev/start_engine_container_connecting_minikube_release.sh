#!/bin/bash
set -e

# Start the engine as a Docker Container connecting to a local Minikube (Kubernetes Mode)
# Pulling is allowed
# This sample script is using Release Images
if docker ps > /dev/null 2>&1; then
  echo "docker works"
  DOCKER_CALL="docker"
else
  echo "docker seems to need sudo"
  DOCKER_CALL="sudo docker"
fi

## Tricky: Giving access to minikube
# Inspecting the default local docker network (also see docker inspect bridge)
DOCKER_HOST_IP=`$DOCKER_CALL inspect --format='{{range .IPAM.Config}}{{.Gateway}}{{end}}' bridge`
echo "Docker Host IP: ${DOCKER_HOST_IP}"
# Starting kubectl proxy
kubectl proxy --address ${DOCKER_HOST_IP} --port 8001  --accept-hosts='^.*' &

## Accessing ingresses in Minikube (especially tinyproxy)
# Figuring out IP of Minikube
MINIKUBE_IP=`minikube ip`
echo "Minikube IP: ${MINIKUBE_IP}"

# Create Data Volumes
$DOCKER_CALL volume create mantik-engine-repo
$DOCKER_CALL volume create mantik-engine-logs

$DOCKER_CALL run --rm \
  -v mantik-engine-repo:/data/mantik/repo \
  -v mantik-engine-logs:/data/mantik/logs \
  -e SKUBER_URL=http://${DOCKER_HOST_IP}:8001 \
  -p 8087:8087                                  `# gRpc API` \
  --add-host=minio.minikube:${MINIKUBE_IP}      `# Minio needs to be resolved` \
  mantikai/engine:v0.3.0-test6 \
  -Dmantik.executor.kubernetes.nodeAddress=${MINIKUBE_IP} `# Where to look for bound services`

