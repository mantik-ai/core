#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

sbt -Dconfig.resource=application_local_microk8s.conf executorApp/run