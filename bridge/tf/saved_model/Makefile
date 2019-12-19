NAME=tfbridge
EXTRA_DEPS=vendor/tensorflow_c/lib docker_build
UNAME_S?=$(shell uname -s)
CLIBDIR = vendor/tensorflow_c
TENSORFLOW_DIR = $(CLIBDIR)/lib
ENABLE_LINUX=false
DOCKER_IMAGE_NAME=bridge.tf.saved_model

# This makefile is a bit ugly because we need the C-Library linked to the Executable.

ifeq ($(UNAME_S), Darwin)
  DYLD_LIBRARY_PATH=$(shell pwd)/$(TENSORFLOW_DIR)
  export DYLD_LIBRARY_PATH
else ifeq ($(UNAME_S), Linux)
  LD_LIBRARY_PATH=$(shell pwd)/$(TENSORFLOW_DIR)
  export LD_LIBRARY_PATH
else
  $(error Unknown platform)
endif

CGO_LDFLAGS = -L$(shell pwd)/$(TENSORFLOW_DIR)
export CGO_LDFLAGS

MANTIK_ROOT = ../../../

include $(MANTIK_ROOT)/scripts/ci/Makefile.go
include $(MANTIK_ROOT)/scripts/ci/Makefile.docker_single

clean::
	rm -rf docker_build

# Pulling dependencies
vendor/tensorflow_c/lib:
	./pull_dependencies.sh

# Prepare Docker image build directory, referenced the Dockerfile
# (In our dockerfile the target is rebuild, for stabilizing the linkage)
docker_build: $(shell find $(MANTIK_ROOT)/go_shared -name *.go)
	mkdir -p docker_build
	cp -r $(MANTIK_ROOT)/go_shared docker_build/