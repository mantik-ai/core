#!/bin/bash

# Simple helm install that overrides some of the values from the helm chart as needed for mantik engine deployment from environment variables or defaults.

DEPLOYMENT_NAME=${HELM_DEPLOYMENT_NAME:-me}
NAMESPACE=${HELM_NAMESPACE:-me}
PULL_POLICY=${PULL_POLICY:-IfNotPresent}
ENGINE_IMAGE=${ENGINE_IMAGE:-mantikai/engine}
ENGINE_TAG=${ENGINE_TAG:-v0.3.0-rc5}
DEFAULT_IMAGE_TAG=${MANTIK_DEFAULT_TAG:-v0.3.0-rc5}
DISABLE_PULL=${DISABLE_PULL:-"false"}
NODE_ADDRESS=$(minikube ip)

helm install $DEPLOYMENT_NAME . --namespace $NAMESPACE --create-namespace --set image.pullPolicy=$PULL_POLICY --set image.name=$ENGINE_IMAGE --set image.tag=$ENGINE_TAG --set deployment.mantik.args.executor.docker.defaultImageTag=$DEFAULT_IMAGE_TAG --set deployment.mantik.args.executor.behaviour.disablePull=$DISABLE_PULL --set deployment.mantik.args.executor.kubernetes.nodeAddress=$NODE_ADDRESS
