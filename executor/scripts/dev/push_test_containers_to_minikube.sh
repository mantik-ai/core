#!/usr/bin/env bash
set -e

# Push the test containers which are built through coordinator/create_docker_images.sh to the MiniKube repository

DOCKER_TLS_VERIFY_ORIGINAL=$DOCKER_TLS_VERIFY
DOCKER_HOST_ORIGINAL=$DOCKER_HOST
DOCKER_CERT_PATH_ORIGINAL=$DOCKER_CERT_PATH
DOCKER_API_VERSION_ORIGINAL=$DOCKER_API_VERSION

# Rude check if docker can be used without sudo
# (On Linux Workstation I do not want to be docker runnable without, due security concerns)
if docker ps > /dev/null 2>&1; then
    echo "Docker works"
    DOCKER_CALL="docker"
else
    echo "Docker seems to need sudo"
    DOCKER_CALL="sudo docker"
fi

function transfer_image {
    local FILENAME=$1.tgz.temp
    echo "Saving $1 to $FILENAME..."
    $DOCKER_CALL save $1 | gzip > $FILENAME

    eval $(minikube docker-env)
    echo "Loading $FILENAME into Minikube"
    gunzip -c $FILENAME | docker load

    # Undoing docker environment
    export DOCKER_TLS_VERIFY=$DOCKER_TLS_VERIFY_ORIGINAL
    export DOCKER_HOST=$DOCKER_HOST_ORIGINAL
    export DOCKER_CERT_PATH=$DOCKER_CERT_PATH_ORIGINAL
    export DOCKER_API_VERSION=$DOCKER_API_VERSION_ORIGINAL
    echo "Deleting Image File..."
    rm $FILENAME
}

transfer_image coordinator
transfer_image payload_preparer
transfer_image executor_sample_source
transfer_image executor_sample_sink
transfer_image executor_sample_learner
transfer_image executor_sample_transformer