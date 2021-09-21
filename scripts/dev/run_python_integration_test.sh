#!/usr/bin/env bash

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

echo $0

./scripts/dev/start_engine_minikube.sh &
trap "kill 0" EXIT # Kills children processes at the end

sleep 30

POETRY_CACHE_DIR_PATH=${PWD}/cache/poetry
cd ./examples/python
POETRY_CACHE_DIR=${POETRY_CACHE_DIR_PATH} poetry install
for file in *.py; do
  POETRY_CACHE_DIR=${POETRY_CACHE_DIR_PATH} poetry run python $file
done
