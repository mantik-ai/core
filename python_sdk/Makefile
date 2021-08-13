include ../scripts/ci/Makefile.grpc_helper

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

docker:
	@#"Nothing to do"

VERSION=$(CI_COMMIT_TAG:v%=%)
publish:
	@if [ -z ${VERSION} ]; then echo "Version is empty"; false; fi
	echo "Publishing version ${VERSION}"
	# Updating MNP Dependency
	sed -i 's/.*mnp.*/mnp = "${VERSION}"/' pyproject.toml

	poetry version ${VERSION}
	poetry publish --build --username $PYPI_USERNAME --password $PYPI_PASSWORD

docker-unchecked:

docker-publish:

.PHONY: api-doc build clean test typecheck docker docker-unchecked docker-publish1