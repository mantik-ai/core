#!/usr/bin/env bash
# Deploy needed helper containers to local microk8s
# (This are all bridges and coordinator containers)
# Note: assumed that it's built

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

. ./scripts/ci/docker_help.sh

# First rebuild all images
./scripts/dev/create_docker_images_all.sh

transfer_image() {
    REMOTE_NAME=localhost:32000/$1:latest
    echo "Saving $1 to $REMOTE_NAME..."
    $DOCKER_CALL tag $1 $REMOTE_NAME
    $DOCKER_CALL push $REMOTE_NAME
}


transfer_image binary_bridge
transfer_image bridge.sklearn.simple
transfer_image select_bridge
transfer_image bridge.tf.saved_model
transfer_image bridge.tf.train
transfer_image coordinator
transfer_image payload_preparer
transfer_image executor_sample_source
transfer_image executor_sample_sink
transfer_image executor_sample_learner
transfer_image executor_sample_transformer
