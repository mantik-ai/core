#!/usr/bin/env bash

set -e
MYDIR=`dirname $0`
cd $MYDIR


# I added the protobuf files to the repository, but in case you want to update them
# Instaling Protobuf for GO
GOPATH_TO_USE=${GOPATH:-$HOME/go}
echo "Using GOPATH $GOPATH_TO_USE"
go get -u github.com/golang/protobuf/protoc-gen-go
export PATH=$PATH:$GOPATH_TO_USE/bin


TENSORFLOW_DIR=${TENSORFLOW_OVERRIDE:-/Users/nos/compiling/tensorflow}
if [[ -d $TENSORFLOW_DIR ]]; then
    echo "Using Tensorflow directory in $TENSORFLOW_DIR"
else
    # TODO: Download it somewhere
    echo "Tensorflow directory not found"
    exit 1
fi
PROTOC_OPTS="-I $TENSORFLOW_DIR --go_out=import_prefix=tfbridge/pb_vendor/:pb_vendor"
mkdir -p pb_vendor
protoc $PROTOC_OPTS $TENSORFLOW_DIR/tensorflow/core/framework/*.proto
protoc $PROTOC_OPTS $TENSORFLOW_DIR/tensorflow/core/protobuf/{saver,meta_graph,saved_model}.proto
protoc $PROTOC_OPTS $TENSORFLOW_DIR/tensorflow/core/lib/core/*.proto
# Ugly Hack, As I couldn't get Protobuf Golang Generator to work together with Go Module support
# All imports of the protoc-Code need to be prefixed with module name, this is already done with import_prefix
# However it also then imports the protobuf library itself in this way, which we do not want.
# If someone has a clever Idea, tell me, we already lost one afternoon for that.

# Note: if this fails on MAC, add "" after "-i"
sed -i 's:"tfbridge/pb_vendor/github.com/golang/protobuf/:"github.com/golang/protobuf/:g' `find pb_vendor -name "*.go" | xargs`
