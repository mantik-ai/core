#!/usr/bin/env bash
set +e
MYDIR=`dirname $0`
cd $MYDIR/../..

echo $0

./scripts/dev/start_engine_minikube.sh &
kill_engine(){
  ps -ax | grep engineApp/run | grep sbt |  cut -f1 -d " " | xargs kill
}
trap kill_engine EXIT

sleep 15
# ./scripts/dev/create_docker_images_all_minikube.sh

cd ./python_sdk/
pipenv install --dev
cd examples
for file in *.py; do
  pipenv run python $file
done
