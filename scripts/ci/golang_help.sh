#!/usr/bin/env sh
# Note: this script is intended to be sourced in via source

# Script for building simple go applications

golang_test(){
    APP_NAME=$1

    echo "Testing... $APP_NAME"
    go test -v ./...
}

golang_build(){
    APP_NAME=$1
    echo "Formatting... $APP_NAME"
    gofmt -w .

    mkdir -p target

    APP_NAME_LINUX=${APP_NAME}_linux

    echo "Building main application $APP_NAME"
    go build -o target/$APP_NAME main.go

    echo "Building Linux Application $APP_NAME_LINUX"
    CGO_ENABLED=0 GOOS=linux go build -a -o target/$APP_NAME_LINUX main.go
}

golang_build_and_test(){
    golang_build $1
    golang_test $1
}
