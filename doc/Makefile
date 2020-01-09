.PHONY: build
build: ../public

clean:
	rm -rf ../public

../public:
	pipenv --python 3 install # automatically uses requirements.txt
	cp ../Readme.rst index.rst
	pipenv run sphinx-build -b html . ../public

.PHONY: test
test:
	# Empty

.PHONY: docker
docker:
	@# Nothing to do

.PHONY: docker-publish
docker-publish:
	@# Nothing to do