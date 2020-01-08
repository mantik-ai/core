#!/usr/bin/env bash
# Calls the Mantik Build Docker Image with `make`
# The Docker Image will be run with the UID/GID of the current user
# so that build results are not made as ROOT.
set -e
MYDIR=`dirname $0`
cd $MYDIR
mkdir -p cache
sudo docker run -it --rm -e USER_ID=`id -u` -e GROUP_ID=`id -g` -e CACHE_DIR=/work/cache -v $PWD:/work mantikbuilder make "$@"


