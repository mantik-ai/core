#!/usr/bin/env bash
set -e

# Push the test containers which are built through coordinator/create_docker_images.sh to the Microk8s repository
# Note: the repository is listening on localhost:32000

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
    REMOTE_NAME=localhost:32000/$1:latest
    echo "Saving $1 to $REMOTE_NAME..."
    $DOCKER_CALL tag $1 $REMOTE_NAME
    $DOCKER_CALL push $REMOTE_NAME
}

transfer_image coordinator
transfer_image payload_preparer
transfer_image executor_sample_source
transfer_image executor_sample_sink
transfer_image executor_sample_learner
transfer_image executor_sample_transformer