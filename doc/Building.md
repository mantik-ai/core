Building Mantik
===============

Introduction
------------
Mantik Core is written in Scala, Go and Python. It's artifacts are build partly as Docker Images. 
This makes the build a bit complicated. 

Requirements
------------

* Scala Code
    * Java OpenJDK. 1.8 should do it, but 1.11 is faster

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

    * [Go](https://golang.org/dl/). Although 1.11.x (x >= 10) should do it, new code will be using 1.13
      and the build server will be migrated to 1.13
    * Protobuf

      ```
      # Ubuntu 18.04
      sudo apt-get install protobuf-compiler libprotobuf-dev
      ```
      
    * Protobuf for golang

      ```
      go get github.com/golang/protobuf/protoc-gen-go
      ```
      
* Python
     
     - pipenv
     - Python dev (gcc etc.)
     
     The python code is using generated protobuf code. After a clean checkout
     run a full build before developing within the IDE.       

* Integration tests and running
    * [Minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/)
    
      Minikube is used as developing Kubernetes backend, for integration tests
      and also for developing with the Docker Backend without needing root-Permissions.
      
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


Running Unit Tests
------------------

All Unit tests can be run via `make test`.

If you want to concentrate on Scala Unit tests, you can also issue `test` in the SBT Shell.

Open in IDE
-----------

* The mantik-core directory can be opened and edited in IntelliJ
  
  IntellIJ doesn't automatically generate gRpc stubs. You can force evaluation using `test:compile` in the SBT shell.  
  
* All go applications can be edited in Goland, and it seems as also Visual Studio Code is working with code completion.
  (However it looks as you can only open one project, not all-at-once).


Running Integration Tests
-------------------------

* The (Scala) integration tests are expecting a running Minikube.
* This can be started using `scripts/dev/start_minikube.sh`
* You can execute them directly in IntelliJ (they are annotated with `@IntegrationTest` and thus 
  excluded from a regular test run)
* All integration tests, including (re-)starting Minikube, can be done using `make integration-test`

          