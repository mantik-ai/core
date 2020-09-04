# Minikube Compatibility

It is possible to debug Mantik Engine locally using Minikube.

## Preparation

- You must have [Minikube](https://kubernetes.io/docs/setup/minikube/) up and running.
- You must habe built all Artefacts: 
  
      make

## Building Images directly inside Minikube

In contrast to Mikrok8s, Minikube provides it's own Docker Context, where images can be found and also deployed to.

This Docker context can be used for building images, use the script 

    make docker-minikube 

## Start the Engine

Now you can directly execute the engine within IntelliJ or by using 
    
    scripts/dev/start_engine_minikube.sh
    
## Start the samples

Now you can directly execute the samples inside `examples` folder within IntelliJ.

They will connect to a running engine.
    

## Start the Python Example

With the Engine running, the python client example should be runnable from pipenv.

## TLDR

- start minikube
- build all
- build docker images in Minikube
- start executor for Minikube
- start engine
- start python example
 
