#!/usr/bin/env sh
# Note: the Base image has no bash installed, we are restricted to sh.
set -e

MYDIR=`dirname $0`
cd $MYDIR

echo "Building"
docker build -t tfbridge .

IMAGE_NAME=mdocker.rcxt.de/tf.savedmodel:$CI_COMMIT_REF_SLUG
echo "Renaming to $IMAGE_NAME"
docker tag tfbridge $IMAGE_NAME

docker info

echo "Docker Login..."
docker login -u mantik.ci -p $SONATYPE_MANTIK_PASSWORD mdocker.rcxt.de

echo "Pushing"
docker push $IMAGE_NAME
