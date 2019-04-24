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


# Push the given image to mdocker.rcxt.de
docker_push(){
    if [ -n "$ENABLE_PUSH" ]; then
        echo "Docker Login..."
        docker login -u mantik.ci -p $SONATYPE_MANTIK_PASSWORD mdocker.rcxt.de

        IMAGE_NAME=mdocker.rcxt.de/$1:$CI_COMMIT_REF_SLUG
        echo "Handling Image $1 ---> $IMAGE_NAME"

        docker tag $1 $IMAGE_NAME
        docker push $IMAGE_NAME

        echo "Pushing Done"
    else
        echo "Pushing disabled"
    fi
}

