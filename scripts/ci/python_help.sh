#!/usr/bin/env sh
# Note: this script is intended to be sourced in via source


# Helper scripts for building python applications

# Note: we assume that $MANTIK_ROOT is set

if [ -z "$MANTIK_ROOT" ]; then
    echo "\$MANTIK_ROOT not set"
    exit 1
fi

# Create requirements file and prepares for a build of python applications
python_prepare_pip_build(){
    # Mantik lib is in a upper level folder usually
    # Unfortunately, this doesn't work well with Docker as it needs all dependencies inside the docker root
    # so we copy it back to the right solution

    mkdir -p target
    pipenv lock -r > target/requirements.txt
    cp -r $MANTIK_ROOT/python_shared target/

    sed -i '/python_shared/c\-e ./target/python_shared\' target/requirements.txt
}