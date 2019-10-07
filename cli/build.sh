#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR

function update_grpc() {
    echo "Updading protos..."
    rm -rf protos
    mkdir -p protos
    GO_DIR=${GO_PATH:-$HOME/go}
    GOPATH_TO_USE=${GOPATH:-$HOME/go}
    export PATH=$PATH:$GOPATH_TO_USE/bin
    protoc -I ../engine/src/main/protobuf ../engine/src/main/protobuf/mantik/engine/*.proto --go_out=plugins=grpc,import_path=protos/engine:protos
}

update_grpc

APP_VERSION=`git describe --always --dirty`
source ./../scripts/ci/golang_help.sh
golang_build mantik
