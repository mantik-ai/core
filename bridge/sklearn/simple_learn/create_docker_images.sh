#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

mkdir -p target


# Create requirements file
set -e
MYDIR=`dirname $0`
cd $MYDIR
pipenv lock -r > target/requirements.txt

# Mantik lib is in a upper level folder (../../ ..)
# Unfortunately, this doesn't work well with Docker as it needs all dependencies inside the docker root
# so we copy it back to the right solution
cp -r ./../../../python_shared target/
sed -i '/python_shared/c\-e ./target/python_shared\' target/requirements.txt


# Build Docker
. ../../../scripts/ci/docker_help.sh

$DOCKER_CALL build -t bridge.sklearn.simple .

docker_push bridge.sklearn.simple
