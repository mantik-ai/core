Building Mantik
===============

Introduction
------------
Mantik Core is written in Scala, Go and Python. It's artifacts are build partly as Docker Images. 
This makes the build a bit complicated. 

Requirements
------------

* Scala Code
    * Java OpenJDK. 11

      ```
      # Ubuntu 18.04
      sudo apt-get install openjdk-11-jdk
      ```
  
    * [SBT](https://www.scala-sbt.org/download.html) as build system for Scala.
    
    * Our testcases need a lot of RAM and some default SBT installations do not have enough, to increase RAM
      you can set the `SBT_OPTS` Environment variable:
      
      ```
      export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -Xmx4G"
      ```

* Go Code

    * [Go](https://golang.org/dl/). We are using Version 1.16.
    * Protobuf

      ```
      # Ubuntu 18.04
      sudo apt-get install protobuf-compiler libprotobuf-dev
      ```
      
* Python
     
     - Python 3.7
     - poetry
     - Python dev (gcc etc.)
     
     The python code is using generated protobuf code. After a clean checkout
     run a full build before developing within the IDE.
     
     Installing python 3.7 on Ubuntu 20.04:
     
     - Add [Deadsnakes PPA](https://launchpad.net/~deadsnakes/+archive/ubuntu/ppa)
     - `apt-get install python3.7`

* Docker Images

     - [Docker](https://docker.io)
       ```
       # Ubuntu 18.04
       apt-get install docker.io
       ```
       For Mac, follow this [Installation Guide](https://docs.docker.com/docker-for-mac/)
    
* JavaScript

     - [NodeJS](https://nodejs.org/en/)
       
       For Ubuntu see [NodeSource](https://github.com/nodesource/distributions#debinstall)
       The Ubuntu-shipped distribution is too old.

* Integration tests and running
    * [Minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) 1.18.1
    
      Minikube is used as developing Kubernetes backend, for integration tests
      and also for developing with the Docker Backend without needing root-Permissions.
      
    * [Kubectl](https://kubernetes.io/docs/reference/kubectl/overview/)
    
      ```
      # Ubuntu
      sudo snap install kubectl --classic
      ```
      
    * [Minio](https://min.io/)
    
      Minio is a free object server with (partly) S3 Compatible API.
      We use it local deployments to publish binary assets to bridges running in Kubernetes.
      
    * [Minio Client](https://min.io/download#/linux)  
    
      We use `mc` for configuring Minio.
      
    * `socat`
    
      ```
      # Ubuntu 18.04
      apt-get install socat
      ```
      
      Socat is needed to create a temporary docker port from a local running docker socket, which can be accessed
      by Mantik without using Admin privileges. 
      
Building Steps
--------------

Building is done with Make.

   `make build`
   
However this doesn't create Docker Images needed to run any practical examples.

There is also a build-in help with `make help`.

In order to do that, there are some alternatives:

   * `make docker`
   
      This creates all docker images on the local docker instance. You can't execute examples in Minikube with this build.
      But you can run in local docker mode.
      
   * `make docker-minikube`
   
      This creates all docker images on a running minikube instance. You can use that in order start integration tests.

Generated Files
---------------

Starting with `v0.5` protobuf-generated files are are tracked in Git for Python and Golang, to make distribution of packages easier.

When you change `.proto`-Files, you have to call `make generated` in order to update generated files.

Running Unit Tests
------------------

All Unit tests can be run via `make test`.

If you want to concentrate on Scala Unit tests, you can also issue `test` in the SBT Shell.

Open in IDE
-----------

* The mantik-core directory can be opened and edited in IntelliJ
  
  IntellIJ doesn't automatically generate gRpc stubs. You can force evaluation using `test:compile` in the SBT shell.
  
  Or calling `make`  
  
* All go applications can be~~~~ edited in Goland, and it seems as also Visual Studio Code is working with code completion.
  (However it looks as you can only open one project, not all-at-once).


Running Integration Tests
-------------------------

* Minikube can be started using `scripts/dev/start_minikube.sh`

  After starting, both `minikube status` and `kubectl get namespaces` should work.  

* Add an entry to minio.minikube to your `/etc/hosts`, using the ip of Minikube (use `minikube ip` for that)
  
  E.g.

  ```
  192.168.99.105 minio.minikube
  ```
  
* Configure minio permissions using `scripts/dev/configure_minio.sh`
* All integration tests can be run using `make integration-test`


Troubleshooting Integration Tests
---------------------------------

* If minio is a non-working state, delete the namespace and recreate it. 

  - Ensure entry in `/etc/hosts` (see above)
  - Delete minio namespace `kubectl delete ns minio`
  - Create Mino (Helm) `/scripts/dev/start_minikube_minio.sh`
  - Configure Minio Permissions `./scripts/dev/configure_minio.sh`

 * Sometimes if minikube got recreated or deleted (e.g. `minikube delete --all`), Helm thinks Minio is already deployed (since it was in an older minikube instance). In that case, use `helm delete minio -n minio` and `/scripts/dev/start_minikube_minio.sh` and configure Minio (see above).

* In seldom cases, the minikube IP changes, then you have to update `/etc/hosts`  

  