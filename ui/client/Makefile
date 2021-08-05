SOURCE_FILES = $(shell git ls-files)

ifdef CACHE_DIR
export NPM_CONFIG_CACHE=$(CACHE_DIR)/npm
endif

.PHONY: build
build: target/build.make

target/build.make: $(SOURCE_FILES)
	npm ci # Like npm install, but for CI
	npm run build
	touch $@

clean:
	rm -rf target

test: build

.PHONY: api-doc test docker install publish docker-unchecked docker-publish

