# Directory where Mantik Protobuf files resides
PROTOBUF_INCLUDE = ../engine/src/main/protobuf/
# Directory where Python generated protobuf code resides
PROTOBUF_OUT = mantik/engine/_stubs
# All protobuf dependency files
PROTOBUF_FILES = $(shell find $(PROTOBUF_INCLUDE) -name "*.proto")

# Caching
ifdef CACHE_DIR
  export PIP_CACHE_DIR=$(CACHE_DIR)/pip
  export PIPENV_CACHE_DIR=$(CACHE_DIR)/pipenv
endif

build: protobuf

.PHONY: clean
clean:
	rm -rf target
	rm -rf $(PROTOBUF_OUT)

.PHONY: test
test: build
	pipenv install --dev
	pipenv run pytest

protobuf: $(PROTOBUF_OUT)/.done.make

# Updates gRPC/Protobuf generators
# See https://grpc.io/docs/tutorials/basic/python/

# Note: the protobuf files lead to the python directory names.
# Due various bugs, it can't be simply changed via python_out/grpc_python_out
# See: https://github.com/grpc/grpc/issues/9575
#      https://github.com/grpc/grpc/issues/9450
#      https://github.com/architect-team/python-launcher/issues/1

$(PROTOBUF_OUT)/.done.make: $(PROTOBUF_FILES)
	mkdir -p $(PROTOBUF_OUT)
	pipenv install --dev
	pipenv run python -m grpc_tools.protoc \
        -I $(PROTOBUF_INCLUDE) \
        --python_out $(PROTOBUF_OUT) \
        --grpc_python_out $(PROTOBUF_OUT) \
        $(PROTOBUF_INCLUDE)/mantik/engine/*.proto
	# make all python imports from mantik.engine relative
	sed -i -E 's/from mantik.engine/from ./g' $(PROTOBUF_OUT)/mantik/engine/*.py
	touch $@

.PHONY: docker
docker:
	@#"Nothing to do"

.PHONY: docker-publish
docker-publish: