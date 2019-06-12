# Minikube Compatibility

It is possible to debug Mantik Engine locally using Minikube.

## Preparation

- You must have [Minikube](https://kubernetes.io/docs/setup/minikube/) up and running.
- You must habe built all Artefacts: 
  
      scripts/dev/build_all.sh

## Building Images directly inside Minikube

In contrast to Mikrok8s, Minikube provides it's own Docker Context, where images can be found and also deployed to.

This Docker context can be used for building images, use the script 

    scripts/dev/create_docker_images_all_minikube.sh 

## Starting Executor with Minikube Support

You can start the executor through the following Script:

    scripts/dev/start_executor_minikube.sh

The script does the following

- Unset `SKUBER_URL` (which you can use to connect to Microk8s). Minikube writes the connect configuration
  into `~/.kube/config` which is automatically picked up by [Skuber](https://github.com/doriordan/skuber/blob/master/docs/Configuration.md) backend.
- Overwrite the configuration of the Executor. Per default images are downloaded from `mdocker.rcxt.de`. But we want to use
  the images we just built above.
  
## Start the samples

Now you can directly execute the samples inside `examples` folder within IntelliJ.

## Start the Engine

Now you can directly execute the engine within IntelliJ or by using 

    
    scripts/dev/start_engine.sh

## Start the Python Example

With the Engine running, the python client example should be runnable from pipenv.

## TLDR

- start minikube
- build all
- build docker images in Minikube
- start executor for Minikube
- start engine
- start python example
 
