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

docker_build executor.coordinator

# Smoke Test
echo "Starting Smoke Test"
$DOCKER_CALL run -t --rm mantikai/executor.coordinator help

echo "Building Helper"
docker_build executor.payload_preparer -f Dockerfile_preparer

echo "Building Pipeline Controller"
docker_build executor.pipeline_controller --build-arg input_executable=target/pipeline_controller

# Creating Sample Containers
echo "Building Sample Sink"
docker_build executor.sample_sink --build-arg input_executable=target/executor_sample_sink_linux
echo "Building Sample Source"
docker_build executor.sample_source --build-arg input_executable=target/executor_sample_source_linux
echo "Building Sample Learner"
docker_build executor.sample_learner --build-arg input_executable=target/executor_sample_learner_linux
echo "Building Sample Transformer"
docker_build executor.sample_transformer --build-arg input_executable=target/executor_sample_transformer_linux

docker_push executor.coordinator
docker_push executor.payload_preparer
docker_push executor.pipeline_controller
docker_push executor.sample_sink
docker_push executor.sample_source
docker_push executor.sample_learner
docker_push executor.sample_transformer
