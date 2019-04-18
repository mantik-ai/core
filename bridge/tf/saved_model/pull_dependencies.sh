#!/usr/bin/env bash
set -e

MYDIR=`dirname $0`
cd $MYDIR

# Tensorflow C-Library
CLIBDIR=$PWD/vendor/tensorflow_c
mkdir -p $CLIBDIR

if [[ -d $CLIBDIR/lib ]]; then
    echo "Library already exists, skipping download"
else
    if [[ "$OSTYPE" == "darwin"* ]];then
        echo "Fetching OSX Tensorflow"
        curl https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-cpu-darwin-x86_64-1.12.0.tar.gz | tar xvz -C $CLIBDIR
    elif [[ "$OSTYPE" == "linux-gnu" ]]; then
        echo "Fetching Linux Tensorflow (CPU)"
        curl https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-cpu-linux-x86_64-1.12.0.tar.gz | tar xvz -C $CLIBDIR
    else
        echo "Unsupported OS $OSTYPE"
        exit 1
    fi
fi
