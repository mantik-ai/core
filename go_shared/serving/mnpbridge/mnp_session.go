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
package mnpbridge

import (
	"context"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"gl.ambrosys.de/mantik/go_shared/protos/mantik/bridge"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"gl.ambrosys.de/mantik/go_shared/util/dirzip"
	"io"
	"io/ioutil"
	"os"
)

type MnpSession struct {
	sessionId         string
	runner            ExecutableRunner
	executable        serving.Executable
	portConfiguration *mnpgo.PortConfiguration
	payloadDir        *string
}

func InitSession(
	sessionId string,
	backend serving.Backend,
	portConfiguration *mnpgo.PortConfiguration,
	configuration *bridge.MantikInitConfiguration,
	callback mnpgo.StateCallback,
) (mnpgo.SessionHandler, error) {
	header, err := serving.ParseMantikHeader([]byte(configuration.Header))
	if err != nil {
		return nil, errors.Wrap(err, "Could not decode Mantik Header")
	}

	var payload *string
	if configuration.Payload != nil {
		callback(mnp.SessionState_SS_DOWNLOADING)
		payload, err = initPayload(configuration, configuration.PayloadContentType)
		if err != nil {
			return nil, errors.Wrap(err, "Could not init payload")
		}
	}

	callback(mnp.SessionState_SS_STARTING_UP)

	executable, err := backend.LoadModel(payload, header)
	if err != nil {
		removePayload(payload)
		return nil, errors.Wrap(err, "Loading model failed")
	}

	adapted, err := serving.AutoAdapt(executable, header)
	if err != nil {
		removePayload(payload)
		return nil, errors.Wrap(err, "Could not adapt model")
	}

	return SessionFromExecutable(sessionId, adapted, portConfiguration, payload)
}

// Downloads and initializes payload. Returns unpacked payload file/directory.
func initPayload(configuration *bridge.MantikInitConfiguration, contentType string) (*string, error) {
	tempFile, err := ioutil.TempFile("", "payloadfile")
	if needsUnpacking(contentType) {
		defer os.Remove(tempFile.Name())
	}
	if err != nil {
		return nil, errors.Wrap(err, "Could not create temporary file")
	}

	url, isUrl := configuration.Payload.(*bridge.MantikInitConfiguration_Url)
	fixContent, isFix := configuration.Payload.(*bridge.MantikInitConfiguration_Content)
	if isUrl {
		err = DownloadPayload(url.Url, tempFile)
	} else if isFix {
		_, err = tempFile.Write(fixContent.Content)
	} else {
		return nil, errors.New("Unsupported payload type")
	}

	if err != nil {
		return nil, errors.Wrap(err, "Could not write payload file")
	}

	err = tempFile.Close()
	if err != nil {
		return nil, errors.New("Could not close file")
	}

	if !needsUnpacking(contentType) {
		name := tempFile.Name()
		return &name, nil
	}

	directory, err := unpackFile(tempFile.Name(), contentType)
	if err != nil {
		return nil, errors.Wrap(err, "Could not unpack payload file")
	}
	return &directory, nil
}

func needsUnpacking(contentType string) bool {
	if contentType == "" {
		// compatibility reasons
		return true
	}
	return contentType == server.MimeZip
}

func unpackFile(tempFile string, contentType string) (string, error) {
	target, err := ioutil.TempDir("", "bridge")
	if err != nil {
		return "", errors.Wrap(err, "Could not create temporary tempPayloadDirectory")
	}
	logrus.Debug("Created temporary tempPayloadDirectory", target)

	if contentType == server.MimeZip || (len(contentType) == 0) {
		if len(contentType) == 0 {
			logrus.Warn("No content type specified, trying unzipping")
		}
		return target, dirzip.UnzipDiectory(tempFile, target, false)
	} else {
		return "", errors.Errorf("Unsupported payload content type %s", contentType)
	}
}

// Remove payload, if it exists
func removePayload(directory *string) {
	if directory != nil {
		err := os.RemoveAll(*directory)
		if err != nil {
			logrus.Error("Could not remove payload directory", err)
		}
	}
}

func SessionFromExecutable(sessionId string, executable serving.Executable, portConfiguration *mnpgo.PortConfiguration, payloadDir *string) (mnpgo.SessionHandler, error) {
	runner, err := NewExecutableRunner(executable, portConfiguration)
	if err != nil {
		return nil, err
	}
	return &MnpSession{
		sessionId:         sessionId,
		runner:            runner,
		executable:        executable,
		portConfiguration: portConfiguration,
		payloadDir:        payloadDir,
	}, nil
}

func (m *MnpSession) Quit() error {
	m.executable.Cleanup()
	removePayload(m.payloadDir)
	return nil
}

func (m *MnpSession) RunTask(
	ctx context.Context,
	taskId string,
	input []io.Reader,
	output []io.WriteCloser,
) error {
	return m.runner.RunTask(
		taskId,
		input,
		output,
	)
}

func (m *MnpSession) Ports() *mnpgo.PortConfiguration {
	return m.portConfiguration
}
