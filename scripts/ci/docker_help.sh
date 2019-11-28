#!/usr/bin/env sh
# Note: this script is intended to be sourced in via source

# Rude check if docker can be used without sudo
# (On Linux Workstation I do not want to be docker runnable without, due security concerns)
if docker ps > /dev/null 2>&1; then
    echo "Docker works"
    DOCKER_CALL="docker"
else
    echo "Docker seems to need sudo"
    DOCKER_CALL="sudo docker"
fi

# Main Registered Docker Repository Name
DOCKER_REPO="mantikai"

# Build a docker image with name from current directory
docker_build(){
  IMAGE_NAME=$DOCKER_REPO/$1
  echo "Building $IMAGE_NAME"
  $DOCKER_CALL build $2 $3 $4 $5 $6 $7 -t $IMAGE_NAME .
}

# Push the given image to mdocker.rcxt.de
docker_push(){
    LOCAL_IMAGE_NAME=$DOCKER_REPO/$1
    if [ -n "$ENABLE_PUSH" ]; then
        echo "Docker Login..."
        $DOCKER_CALL login -u mantik.ci -p $SONATYPE_MANTIK_PASSWORD mdocker.rcxt.de

        REMOTE_IMAGE_NAME=mdocker.rcxt.de/$1:$CI_COMMIT_REF_SLUG
        echo "Handling Image $LOCAL_IMAGE_NAME ---> $REMOTE_IMAGE_NAME"

        $DOCKER_CALL tag $LOCAL_IMAGE_NAME $REMOTE_IMAGE_NAME
        $DOCKER_CALL push $REMOTE_IMAGE_NAME

        echo "Pushing Done"
    else
        echo "Pushing disabled (only checking existence of $LOCAL_IMAGE_NAME)"
        $DOCKER_CALL inspect $LOCAL_IMAGE_NAME > /dev/null
    fi
}
