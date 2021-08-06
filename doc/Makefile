# Caching
ifdef CACHE_DIR
  export POETRY_CACHE_DIR_PATH=$(CACHE_DIR)/poetry
endif

build: ../public

clean:
	rm -rf ../public

../public:
	POETRY_CACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry install
	cp ../Readme.rst index.rst
	POETRY_CACHE_DIR=$(POETRY_CACHE_DIR_PATH) poetry run sphinx-build -b html . ../public

.PHONY: api-doc build clean test docker docker-unchecked docker-publish
