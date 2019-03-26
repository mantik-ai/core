#!/usr/bin/env bash
# Copies tf.saved_model from repo and put it to minikube.

set -e

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

docker pull mdocker.rcxt.de/tf.savedmodel:master
docker tag  mdocker.rcxt.de/tf.savedmodel:master tf.savedmodel
transfer_image tf.savedmodel
