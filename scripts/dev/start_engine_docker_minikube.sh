#!/usr/bin/env bash

set -e
MYDIR=`dirname $0`
cd $MYDIR

./start_engine.sh -Dconfig.resource=application_local_docker_minikube.conf
