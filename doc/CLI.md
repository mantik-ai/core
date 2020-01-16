Mantik Command Line Client
==========================

The Mantik CLI (command line interface) is a commandline application for communicating with a running local Mantik engine.

You can issue basic operations without interacting with Python or Scala.

The tools is called `mantik` and will communicate to a local running `mantik-engine` via `gRpc`.

The basic use is `mantik <command-name>` which will execute the given command.

For a help, call `mantik help` or `mantik <command> --help`

Most commands have a set of flags which help to modify their behaviour.

Item Management
---------------

* Show local items `mantik items`
* Show a single item, including mantik header `mantik item <name>`
* Extract an item into a directory `mantik extract -o <directory> <name>`
* Pack a directory into a mantik item `mantik add -n <name> <directory>`
* Tag items with a new name `mantik tag <name> <new-name>`

Login into a remote Registry
----------------------------

You can log in into a remote registry (e.g. Mantik Hub)

* Login `mantik login`
* Logout `mantik logout`

Transfer items to and from a remote Registry
--------------------------------------------

You can download and upload mantik items to a remote registry (e.g. Mantik Hub).

In order to do so, you have to be logged in. In future it should be possible to download
public available items without being logged in.

* Download `mantik pull <name>`
* Uploading `mantik push <name>`

Deploy Algorithms or Pipelines
------------------------------

It is possible to deploy items on your local mantik engine, using Docker or Kubernetes.

* Deploy `mantik deploy <name>`
