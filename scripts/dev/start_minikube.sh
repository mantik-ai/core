#!/usr/bin/env bash

set -e
MYDIR=`dirname $0`
cd $MYDIR

# Assuming minikube is installed properly
echo "Checking Minikube ..."
minikube version

echo "Checking helm ..."
helm version

# You can disable recreation with SKIP_RECREATION=true
# as it takes a long time.
if [ -n "$SKIP_RECREATION" ]; then
    echo "Skipping recreation"
else
    echo "Deleting Previous Minikube ..."
    minikube delete # this should return 0 if not existant
    sleep 10        # Sometimes there seems to be some kind of race condition

    echo "Starting Minikube ..."
    minikube start --memory 8192
    minikube addons enable ingress
    minikube addons enable ingress-dns # newer Minikube required?

    ./start_minikube_minio.sh
fi
