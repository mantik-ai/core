#!/usr/bin/env sh
# Note: no bash available on build system

set -e
MYDIR=`dirname $0`
cd $MYDIR

if [ ! -f target/coordinator ]; then
    echo "Build doesn't exist, call build.sh first!"
    exit 1
fi

# Rude check if docker can be used without sudo
# (On Linux Workstation I do not want to be docker runnable without, due security concerns)
if docker ps > /dev/null 2>&1; then
    echo "Docker works"
    DOCKER_CALL="docker"
else
    echo "Docker seems to need sudo"
    DOCKER_CALL="sudo docker"
fi


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


if [ -n "$ENABLE_PUSH" ]; then
    echo "Docker Login..."
    docker login -u mantik.ci -p $SONATYPE_MANTIK_PASSWORD mdocker.rcxt.de

    handle_image () {
        IMAGE_NAME=mdocker.rcxt.de/$1:$CI_COMMIT_REF_SLUG
        echo "Handling Image $1 ---> $IMAGE_NAME"
        docker tag $1 $IMAGE_NAME
        docker push $IMAGE_NAME
    }

    handle_image coordinator
    handle_image payload_preparer
    handle_image executor_sample_sink
    handle_image executor_sample_source
    handle_image executor_sample_learner
    handle_image executor_sample_transformer
    echo "Pushing Done"
else
    echo "Pushing disabled"
fi