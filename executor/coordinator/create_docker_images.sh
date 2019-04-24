#!/usr/bin/env sh
# Note: no bash available on build system

set -e
MYDIR=`dirname $0`
cd $MYDIR

if [ ! -f target/coordinator ]; then
    echo "Build doesn't exist, call build.sh first!"
    exit 1
fi

. ./../../scripts/ci/docker_help.sh

$DOCKER_CALL build -t coordinator .

# Smoke Test
echo "Starting Smoke Test"
$DOCKER_CALL run -t --rm coordinator help

echo "Building Helper"
$DOCKER_CALL build -t payload_preparer -f Dockerfile_preparer .

# Creating Sample Containers
echo "Building Sample Sink"
$DOCKER_CALL build --build-arg input_executable=target/executor_sample_sink_linux   -t executor_sample_sink .
echo "Building Sample Source"
$DOCKER_CALL build --build-arg input_executable=target/executor_sample_source_linux -t executor_sample_source .
echo "Building Sample Learner"
$DOCKER_CALL build --build-arg input_executable=target/executor_sample_learner_linux -t executor_sample_learner .
echo "Building Sample Transformer"
$DOCKER_CALL build --build-arg input_executable=target/executor_sample_transformer_linux -t executor_sample_transformer .

docker_push coordinator
docker_push payload_preparer
docker_push executor_sample_sink
docker_push executor_sample_source
docker_push executor_sample_learner
docker_push executor_sample_transformer
