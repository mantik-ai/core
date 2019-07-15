#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

# Assuming minikube is installed properly
echo "Checking Minikube ..."
minikube version

# Assuming SBT is installed correctly
command -v sbt

# You can disable recreation with SKIP_RECREATION=true
# as it takes a long time.
if [ -n "$SKIP_RECREATION" ]; then
    echo "Skipping recreation"
else
    echo "Deleting Previous Minikube ..."
    minikube delete # this should return 0 if not existant

    echo "Starting Minikube ..."
    minikube start --memory 8192  --no-vtx-check
    minikube addons enable ingress
fi

echo "** Preparation: Build All **"
./scripts/dev/build_all.sh


echo "** Stage 0: Docker Images **"
./scripts/dev/create_docker_images_all_minikube.sh


echo "** Stage 1 Executor Integration Tests **"

# Unset SKUBER_URL (won't harm if not set)
# The kubernetes client should now find Minkube via ~/.kube/config
unset SKUBER_URL

sbt executorApp/it:test

echo "** Stage 2 Planner Integration Tests **"
sbt planner/it:test

echo "** Stage 3 Engine Integration Tests **"
sbt engine/it:test
