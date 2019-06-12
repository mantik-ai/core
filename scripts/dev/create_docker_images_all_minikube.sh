#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

# Creates all Docker images directly available inside minikube.
# Do not forget to build all (build_all.sh) before.

echo "Using docker instance of minikube"
eval $(minikube docker-env)

./scripts/dev/create_docker_images_all.sh
