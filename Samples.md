Samples
=======

The samples are located in `examples/src/main/scala/com.example.examples` and can be directly run with IntelliJ.

Environment
-----------

- You need a Kubernetes environment running (e.g. Minikube)
- You need to run the Executor process (see [Executor.md](Executor.md))
- You need to provide your Mantik Docker Repository Credentials, so that Kubernetes can find the necessary Docker images
  
  The easiest way (apart from overriding config values) is by setting Environment variables
  for our default repository mdocker.rcxt.de
  
  These are
  
  `SONATYPE_MANTIK_USERNAME`: Your username for mdocker.rcxt.de
  `SONATYPE_MANTIK_PASSWORD`: Your password for mdocker.rcxt.de

  The environment variables need to be present during executor run.


Running in Microk8s
-------------------    

- You need to call Executor with a `SKUBER_URL=http://localhost:8080` environment variable
- If the containers can't talk to each other, please have a look at `microk8s.inspect`.
  If it complaints, that you should run `sudo iptables -P FORWARD ACCEPT`, do so.
  