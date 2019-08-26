#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

# Do not use SKUBER_URL, but find Minikube via ~/.kube/config
unset SKUBER_URL


sbt -Dconfig.resource=application_local_minikube.conf engineApp/run