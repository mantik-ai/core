#!/usr/bin/env bash

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

echo "Run: sudo socat -d TCP4-LISTEN:2375,fork,reuseaddr,bind=127.0.0.1 UNIX-CONNECT:/var/run/docker.sock"

sbt -Dconfig.resource=application_local_docker.conf engineApp/run
