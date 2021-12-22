#!/bin/bash
set -e

# Start the engine as a Docker Container connecting to a local Minikube (Docker Mode)
if docker ps > /dev/null 2>&1; then
  echo "docker works"
  DOCKER_CALL="docker"
else
  echo "docker seems to need sudo"
  DOCKER_CALL="sudo docker"
fi


# Evaluating Minikube Docker Credentials
# This sets $DOCKER_CERT_PATH and $DOCKER_HOST and $DOCKER_TLS_VERIFY
eval $(minikube -p minikube docker-env)

# Figuring out IP of Minikube (for Minio)
MINIKUBE_IP=`minikube ip`
echo "Minikube IP: ${MINIKUBE_IP}"


# Tricky, we need this certificate files available inside the container under the correct UID
# We do this by copying them into a Volume
# Also see https://stackoverflow.com/a/55683656
$DOCKER_CALL volume create mantik-engine-docker-credentials
$DOCKER_CALL container create -u 1000:1000 --name mantik-temp -v mantik-engine-docker-credentials:/data hello-world
$DOCKER_CALL cp $DOCKER_CERT_PATH/key.pem mantik-temp:/data/
$DOCKER_CALL cp $DOCKER_CERT_PATH/cert.pem mantik-temp:/data/
$DOCKER_CALL cp $DOCKER_CERT_PATH/ca.pem mantik-temp:/data/
$DOCKER_CALL container rm mantik-temp

# Create Data Volumes
$DOCKER_CALL volume create mantik-engine-repo
$DOCKER_CALL volume create mantik-engine-logs

$DOCKER_CALL run --rm \
  -v mantik-engine-repo:/data/mantik/repo \
  -v mantik-engine-logs:/data/mantik/logs \
  -v mantik-engine-docker-credentials:/data/docker_certs \
  -p 8087:8087                                  `# gRpc API` \
  --add-host=minio.minikube:${MINIKUBE_IP}      `# Minio needs to be resolved` \
  mantikai/engine \
  -Dmantik.executor.docker.defaultImageTag=""  `# Do not load from a specific tag` \
  -Dmantik.executor.behaviour.disablePull=true `# Do not pull images (they are already there)` \
  -Dmantik.executor.type="docker"              `# Use docker` \
  -Dmantik.executor.payloadProvider="executor"  `# We are Executor Provider` \
  -Dmantik.executor.localPayloadProvider.interface=$DOCKER_HOST_IP `# Bind the FileRepo Server to an IP where it's reachable by Docker` \
  -Dmantik.executor.docker.dockerCertPath="/data/docker_certs" \
  -Dmantik.executor.docker.url=$DOCKER_HOST



