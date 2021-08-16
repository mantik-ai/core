include ../scripts/ci/Makefile.grpc_helper

NAME=mantik_dev
MANTIK_ROOT=./../
DOCKER_IMAGE_NAME=mantik_dev
DOCKER_FILE=$(MANTIK_ROOT)/scripts/ci/Dockerfile.mantik_dev_image
include ./../scripts/ci/Makefile.docker_single
include ./../scripts/ci/Makefile.pythonbridge

target/copy_shared.make: $(shell find $(MANTIK_ROOT)/python_sdk -name "*.py")
	mkdir -p target/python_sdk

	cp $(MANTIK_ROOT)/python_sdk/pyproject.toml target/python_sdk
	cp $(MANTIK_ROOT)/python_sdk/poetry.lock target/python_sdk
	cp -v -r $(MANTIK_ROOT)/python_sdk/mantik target/python_sdk
	cp -v -r $(MANTIK_ROOT)/mnp/mnppython target/

	sed -i 's/".*mnppython"/"\.\/..\/mnppython"/' target/python_sdk/poetry.lock target/python_sdk/pyproject.toml

	touch $@


# Caching
ifdef CACHE_DIR
  export POETRY_CACHE_DIR_PATH=$(CACHE_DIR)/poetry
endif

build: protobuf

clean::
	rm -rf target

test: build
	POETRTY_CHACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry install
	POETRY_CACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry run pytest tests
	# POETRY_CACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry run mypy --config-file pyproject.toml --exclude "_stubs/" mantik

$(eval $(call python_grpc,../engine/src/main/protobuf,mantik/engine/_stubs,mantik.engine))
$(eval $(call python_grpc,../bridge/protocol/protobuf,mantik/bridge/_stubs,mantik.bridge))

VERSION=$(CI_COMMIT_TAG:v%=%)
publish:
	@if [ -z ${VERSION} ]; then echo "Version is empty"; false; fi
	echo "Publishing version ${VERSION}"
	# Updating MNP Dependency
	sed -i 's/.*mnp.*/mnp = "${VERSION}"/' pyproject.toml

	poetry version ${VERSION}
	poetry publish --build --username $${PYPI_USERNAME} --password $${PYPI_PASSWORD}

.PHONY: api-doc build clean test typecheck
