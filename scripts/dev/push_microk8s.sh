#!/usr/bin/env bash
# Deploy needed helper containers to local microk8s
# (This are all bridges and coordinator containers)
# Note: assumed that it's built

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

. ./scripts/ci/docker_help.sh

transfer_image() {
    REMOTE_NAME=localhost:32000/$1:latest
    echo "Saving $1 to $REMOTE_NAME..."
    $DOCKER_CALL tag $1 $REMOTE_NAME
    $DOCKER_CALL push $REMOTE_NAME
}


echo "Deploying Bridge Code"
pushd bridge

pushd binary
./create_docker_images.sh
transfer_image binary_bridge
popd

pushd sklearn/simple_learn
./create_docker_images.sh
transfer_image bridge.sklearn.simple
popd

pushd "select"
./create_docker_images.sh
transfer_image select_bridge
popd


pushd tf/saved_model
# Note: this only works in Linux, as we can't build the executable correctly inside OSX
echo "PWD=$PWD"
./create_docker_images.sh
transfer_image bridge.tf.saved_model
popd

pushd tf/train
./create_docker_images.sh
transfer_image bridge.tf.train
popd


popd

echo "Building Coordinator Code"
pushd executor/coordinator
./create_docker_images.sh
transfer_image coordinator
transfer_image payload_preparer
transfer_image executor_sample_source
transfer_image executor_sample_sink
transfer_image executor_sample_learner
transfer_image executor_sample_transformer

popd




