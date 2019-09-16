#!/usr/bin/env bash

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

echo $0

./scripts/dev/start_engine_minikube.sh &
ENGINE_PID=$!
kill_engine(){
  kill ${ENGINE_PID} || true
}
trap kill_engine EXIT

sleep 30

cd ./python_sdk/
pipenv install --dev
cd examples
for file in *.py; do
  pipenv run python $file
done
