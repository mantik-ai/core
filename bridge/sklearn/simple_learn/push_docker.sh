#!/usr/bin/env sh
# Note: the Base image has no bash installed, we are restricted to sh.
set -e

MYDIR=`dirname $0`
cd $MYDIR

NAME=bridge.sklearn.simple

echo "Building $NAME"
docker build -t $NAME .

IMAGE_NAME=mdocker.rcxt.de/$NAME:$CI_COMMIT_REF_SLUG
echo "Renaming to $IMAGE_NAME"
docker tag $NAME $IMAGE_NAME

docker info

echo "Docker Login..."
docker login -u mantik.ci -p $SONATYPE_MANTIK_PASSWORD mdocker.rcxt.de

echo "Pushing"
docker push $IMAGE_NAME
