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