include ../../scripts/ci/Makefile.grpc_helper

# Caching
ifdef CACHE_DIR
  export POETRY_CACHE_DIR_PATH=$(CACHE_DIR)/poetry
endif

build: protobuf

clean::
	rm -rf target

test: build
	POETRY_CACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry install
	POETRY_CACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry run pytest tests
	POETRY_CACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry run mypy --config-file pyproject.toml --exclude "_stubs" mnp

$(eval $(call python_grpc,../protocol/protobuf/,mnp/_stubs,mantik.mnp))

docker:
	@#"Nothing to do"

docker-unchecked:

VERSION=$(CI_COMMIT_TAG:v%=%)
publish:
	@if [ -z ${VERSION} ]; then echo "Version is empty"; false; fi
	echo "Publishing version ${VERSION}"
	poetry version ${VERSION}
	poetry publish --build --username $PYPI_USERNAME --password $PYPI_PASSWORD

docker-publish:

.PHONY: api-doc build clean test docker docker-unchecked docker-publish
