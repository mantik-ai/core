#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

export SKUBER_URL=http://localhost:8080
sbt -Dconfig.resource=application_local_microk8s.conf executorApp/run