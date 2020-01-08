# Initializes Linker flags for building and executing

# Note: this script is to be started with `source`
# as it changes search paths

if [ "$DIR_OVERRIDDEN" = true ]; then
    MYDIR="."
else
    MYDIR=`dirname $0`
fi

CLIBDIR=$PWD/$MYDIR/target/vendor/tensorflow_c
export TENSORFLOW_DIR=$CLIBDIR/lib

if [[ ! -d $TENSORFLOW_DIR ]]; then
    echo "Tensorflow C-Lib Directory does not exists, call build.sh first"
    exit 1
fi

if [[ "$OSTYPE" == "darwin"* ]];then
    export DYLD_LIBRARY_PATH=$TENSORFLOW_DIR
elif [[ "$OSTYPE" == "linux-gnu" ]]; then
    export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$TENSORFLOW_DIR
else
    echo "Unsupported OS $OSTYPE"
    exit 1
fi

export CGO_LDFLAGS="-L$TENSORFLOW_DIR"
