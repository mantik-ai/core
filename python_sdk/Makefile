include ../scripts/ci/Makefile.grpc_helper

# Caching
ifdef CACHE_DIR
  export PIP_CACHE_DIR=$(CACHE_DIR)/pip
  export PIPENV_CACHE_DIR=$(CACHE_DIR)/pipenv
endif

build: protobuf

.PHONY: clean
clean::
	rm -rf target

.PHONY: test
test: build
	pipenv install --dev
	pipenv run pytest
	# pipenv run mypy --ignore-missing-imports mantik

$(eval $(call python_grpc,../engine/src/main/protobuf,mantik/engine/_stubs,mantik.engine))
$(eval $(call python_grpc,../bridge/protocol/protobuf,mantik/bridge/_stubs,mantik.bridge))

.PHONY: docker
docker:
	@#"Nothing to do"

.PHONY: docker-publish
docker-publish: