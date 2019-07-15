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

    rm -rf target
    mkdir -p target
    pipenv lock -r > target/requirements.txt
    cp -r $MANTIK_ROOT/python_sdk target/

    sed -i '/python_sdk/c\-e ./python_sdk\' target/requirements.txt
}

# Prepares a build in target, including requirements.txt file
# All python files are moved there, excluding "target" and "example"
python_build_standard(){
    mkdir -p parent
    python_prepare_pip_build
    # Bug: Not OSX Compatible (-p is not the same as --parents!)
    cp --parents `find . -name "*.py" -not -path "./target/*" -not -path "./example/*"` target/
}
