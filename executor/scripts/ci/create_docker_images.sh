#!/usr/bin/env sh
# Builds and pushes the Executor App to Docker
# (Note: Coordinator has it's own script)
# Requires: Preparation of docker build using SBT's docker:stage Target
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

cd app/target/docker/stage

docker build . -t executor


if [ -n "$ENABLE_PUSH" ]; then
    echo "Docker Login..."
    docker login -u mantik.ci -p $SONATYPE_MANTIK_PASSWORD mdocker.rcxt.de
    IMAGE_NAME=mdocker.rcxt.de/executor:$CI_COMMIT_REF_SLUG
    echo "Final Image name $IMAGE_NAME"
    docker tag executor $IMAGE_NAME
    docker push $IMAGE_NAME
    echo "Pushing Done"
else
    echo "Pushing disabled"
fi