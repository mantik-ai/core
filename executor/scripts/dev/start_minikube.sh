#!/usr/bin/env bash
# Start Mini Kube so that we can develop on it
set -e

minikube start
minikube addons enable dashboard
minikube addons enable registry