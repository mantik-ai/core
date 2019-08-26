package sidecar

import (
	"bytes"
	"coordinator/service/protocol"
	log "github.com/sirupsen/logrus"
	"net/http"
	"net/url"
)

type SideCar struct {
	server *protocol.SideCarServer
	*PlainSidecar
	shutdownWebservice  bool // if true, shutdown the webservice at the end
	webserviceReachable bool
}

func CreateSideCar(settings *protocol.Settings, rootUrl string, shutdownWebservice bool) (*SideCar, error) {
	log.Debugf("Starting side car for %s", rootUrl)
	var s SideCar
	var err error
	url, err := url.Parse(rootUrl)
	if err != nil {
		log.Errorf("Could not parse URL %s", rootUrl)
		return nil, err
	}
	s.server, err = protocol.CreateSideCarServer(settings, &s)
	if err != nil {
		log.Errorf("Could not create server %s", err)
		return nil, err
	}
	s.shutdownWebservice = shutdownWebservice
	s.PlainSidecar = CreateUrlSidecar(url, settings, s.blockUntilWebServiceReachable, s.server.Coordinator)
	return &s, nil
}

func (s *SideCar) Port() int {
	return s.server.Port()
}

// Starts the sidecar, blocking.
func (s *SideCar) Run() error {
	s.server.RunAsync()
	s.server.Ac.StartGoroutine(s.waitWebServiceReachable)

	err := s.server.Ac.WaitGoroutines()

	s.shutdownWebServiceIfEnabled()
	if err != nil {
		return err
	}

	log.Info("Sidecar done")
	return nil
}

// Force close the sidecar (for testcases)
func (s *SideCar) ForceQuit() {
	s.server.ForceQuit()
}

func (s *SideCar) waitWebServiceReachable() {
	err := HttpWaitReachable(s.server.Ac.Context, s.url.String(), s.server.Settings)
	if err != nil {
		s.server.Ac.Fail(err)
		return
	}
	s.lock.Lock()
	defer s.lock.Unlock()
	s.webserviceReachable = true
	return
}

func (s *SideCar) shutdownWebServiceIfEnabled() {
	if s.shutdownWebservice {
		url, err := s.resolveDestination("/admin/quit")
		if err != nil {
			log.Errorf("Could not resolve /admin/quit Call %s", err.Error())
			return
		}
		log.Infof("Calling POST %s", url)
		response, err := http.Post(url, "", &bytes.Buffer{})
		if err != nil {
			log.Errorf("Calling /admin/quit failed: %s", err.Error())
		} else if response.StatusCode != 200 {
			log.Errorf("Calling /admin/quit returned non 200 %d", response.StatusCode)
		}
	}
}

func (s *SideCar) blockUntilWebServiceReachable() error {
	return s.server.Ac.BlockUntil(s.server.Settings.WaitForWebServiceReachable, func() bool {
		s.lock.Lock()
		defer s.lock.Unlock()
		return s.webserviceReachable
	})
}
