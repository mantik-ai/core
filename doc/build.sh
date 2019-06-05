#!/usr/bin/env bash
pip install -r requirements.txt
[ -f index.rst] || ln -s ../Readme.rst index.rst
sphinx-build -b html . ../public
