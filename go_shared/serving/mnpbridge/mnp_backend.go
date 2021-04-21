package mnpbridge

import (
	"github.com/golang/protobuf/ptypes"
	"github.com/golang/protobuf/ptypes/any"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/go_shared/protos/mantik/bridge"
	"gl.ambrosys.de/mantik/go_shared/serving"
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