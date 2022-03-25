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
	"github.com/golang/protobuf/ptypes"
	"github.com/golang/protobuf/ptypes/any"
	"github.com/mantik-ai/core/go_shared/protos/mantik/bridge"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/mantik-ai/core/mnp/mnpgo"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
)

// Wraps the old backend into an MnpHandler
type MnpBackend struct {
	backend     serving.Backend
	name        string
	quitHandler QuitHandler // optional
}

type QuitHandler func()

func NewMnpBackend(backend serving.Backend, name string, quitHandler QuitHandler) (*MnpBackend, error) {
	return &MnpBackend{
		backend:     backend,
		name:        name,
		quitHandler: quitHandler,
	}, nil
}

func (m *MnpBackend) About() (*mnpgo.AboutResponse, error) {
	extra, err := ptypes.MarshalAny(&bridge.BridgeAboutResponse{})
	if err != nil {
		return nil, err
	}
	return &mnpgo.AboutResponse{
		Name:  m.name,
		Extra: extra,
	}, nil
}

func (m *MnpBackend) Quit() error {
	if m.quitHandler != nil {
		m.quitHandler()
	}
	return nil
}

func (m *MnpBackend) Init(
	sessionId string,
	configuration *any.Any,
	contentTypes *mnpgo.PortConfiguration,
	stateCallback mnpgo.StateCallback,
) (mnpgo.SessionHandler, error) {
	var conf bridge.MantikInitConfiguration
	err := ptypes.UnmarshalAny(configuration, &conf)
	if err != nil {
		return nil, errors.Wrap(err, "Could not decode mantik configuration")
	}
	logrus.Infof("Starting session %s", sessionId)
	return InitSession(sessionId, m.backend, contentTypes, &conf, stateCallback)
}
