/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package main

import (
	"context"
	"encoding/base64"
	"flag"
	"github.com/golang/protobuf/ptypes/empty"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"google.golang.org/grpc"
	"google.golang.org/protobuf/proto"
	"io"
	"io/ioutil"
	"os"
	"strings"
	"time"
)

/*
Responsible for initializing MNP Nodes
*/
const RcBadArgs = 1
const RcBadInit = 2
const RcConnectError = 3
const RcInitError = 4
const InitEnv = "MNP_INIT"

func main() {
	logrus.SetLevel(logrus.DebugLevel)
	options := flag.NewFlagSet("preparer", flag.ExitOnError)
	address := options.String("address", "localhost:8502", "The host to initialize")
	initArg := options.String("init", "", "Serialized init call (otherwise MNP_INIT env is used)")
	// KeepRunning may be necessary on Kubernetes so that a Pod is not restarted
	keepRunning := options.Bool("keepRunning", false, "If true, keep the initializer running at the end")
	err := options.Parse(os.Args[1:])
	println("Address=", *address)
	if err != nil {
		logrus.Error("Bad arguments: ", err)
		os.Exit(RcBadArgs)
	}

	initCall, err := parseInit(*initArg)
	if err != nil {
		logrus.Error("Bad init call: ", err)
		os.Exit(RcBadInit)
	}

	logrus.Debug("Decoded init call, connecting...")
	mnpClient, clientCon, err := connect(*address)
	if err != nil {
		logrus.Error("Could not connect: ", err)
		os.Exit(RcConnectError)
	}
	defer clientCon.Close()
	logrus.Debug("Starting init call")
	initClient, err := mnpClient.Init(context.Background(), initCall)
	if err != nil {
		logrus.Error("Error on init", err)
		os.Exit(RcInitError)
	}

	err = waitForFinalState(initClient)
	if err != nil {
		logrus.Error("Error on init: ", err)
		os.Exit(RcInitError)
	}

	logrus.Info("Initializing node done")
	err = clientCon.Close()
	if err != nil {
		logrus.Warn("Could not close connection: ", err)
		// not a hard problem
	}

	if *keepRunning {
		logrus.Info("Keeping Running as requested")
		for {
			time.Sleep(1 * time.Second)
		}
	}
}

func parseInit(argValue string) (*mnp.InitRequest, error) {
	if len(argValue) == 0 {
		argValue = os.Getenv(InitEnv)
	}

	if len(argValue) == 0 {
		return nil, errors.New("No argument and no environment variable given")
	}

	return parseInitFromStringContent(argValue)
}

func parseInitFromStringContent(s string) (*mnp.InitRequest, error) {
	// @ is no part of Base64
	if s[0] == '@' {
		fileName := s[1:]
		fileContent, err := ioutil.ReadFile(fileName)
		if err != nil {
			return nil, err
		}
		return parseInitFromContent(fileContent)
	}
	reader := strings.NewReader(s)
	decoder := base64.NewDecoder(base64.StdEncoding, reader)
	content, err := ioutil.ReadAll(decoder)
	if err != nil {
		return nil, errors.Wrap(err, "Could not read base64 code")
	}
	return parseInitFromContent(content)
}

func parseInitFromContent(bytes []byte) (*mnp.InitRequest, error) {
	req := mnp.InitRequest{}
	err := proto.Unmarshal(bytes, &req)
	if err != nil {
		return nil, err
	}
	return &req, nil
}

func connect(address string) (mnp.MnpServiceClient, *grpc.ClientConn, error) {
	timeout := 60 * time.Second
	retry := 500 * time.Millisecond
	start := time.Now()
	endOfTime := start.Add(timeout)
	var lastErr error = errors.New("Never tried")
	for {
		mnpClient, clientCon, err := tryConnect(address)
		if err == nil {
			logrus.Infof("Connection took %s", time.Now().Sub(start).String())
			return mnpClient, clientCon, nil
		}
		lastErr = err
		time.Sleep(retry)
		if time.Now().After(endOfTime) {
			return nil, nil, lastErr
		}
	}
}

func tryConnect(address string) (mnp.MnpServiceClient, *grpc.ClientConn, error) {
	clientCon, err := grpc.Dial(address, grpc.WithInsecure())
	if err != nil {
		logrus.Debugf("Dialing %s failed %s", address, err.Error())
		return nil, nil, err
	}
	serviceClient := mnp.NewMnpServiceClient(clientCon)

	aboutResponse, err := serviceClient.About(context.Background(), &empty.Empty{})
	if err != nil {
		logrus.Debugf("About call on %s failed %s", address, err.Error())
		clientCon.Close()
		return nil, nil, err
	}
	logrus.Infof("Connected to %s: %s", address, aboutResponse.Name)
	return serviceClient, clientCon, nil
}

func waitForFinalState(initClient mnp.MnpService_InitClient) error {
	for {
		response, err := initClient.Recv()
		if err == io.EOF {
			return errors.New("Got no terminal init response")
		}
		if err != nil {
			return err
		}
		logrus.Infof("Received state: %s", response.State.String())
		if response.State == mnp.SessionState_SS_READY {
			return nil
		}
		if response.State == mnp.SessionState_SS_FAILED {
			return errors.Errorf("Session init failed %s", response.Error)
		}
	}
}
