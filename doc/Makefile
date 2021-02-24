.PHONY: build
build: ../public

clean:
	rm -rf ../public

../public:
	pipenv --python 3 install # automatically uses requirements.txt
	cp ../Readme.rst index.rst
	pipenv run sphinx-build -b html . ../public

# Empty Goals
.PHONY: test
test:

.PHONY: docker
docker:

.PHONY: docker-unchecked
docker-unchecked:

.PHONY: docker-publish
docker-publish:
