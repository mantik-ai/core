package sidecar

import (
	"coordinator/service/protocol"
	"coordinator/service/streamer"
	"errors"
	log "github.com/sirupsen/logrus"
	"net/url"
	"sync"
)

// Implements the sidecar interface
type PlainSidecar struct {
	// May be empty, if not attached to a URL
	url                 *url.URL
	settings            *protocol.Settings
	getStreams          []*streamer.HttpGetSource
	postSinks           []*streamer.HttpPostSink
	postTransformations []*streamer.HttpPostTransformation
	// protects the various lists
	lock sync.Mutex
	// May be set to block calls in the beginning (usually until the Webservice is available)
	startBlocker func() error
	// Provides the coordinator
	coordinatorProvider func() (protocol.CoordinatorService, error)
}

/** Create a Sidecar for a URL. */
func CreateUrlSidecar(url *url.URL, settings *protocol.Settings, startBlocker func() error, coordinatorProvider func() (protocol.CoordinatorService, error)) *PlainSidecar {
	return &PlainSidecar{
		url:                 url,
		settings:            settings,
		startBlocker:        startBlocker,
		coordinatorProvider: coordinatorProvider,
	}
}

/** Create a headless Sidecar (without URL). */
func CreateHeadlessSideCar(settings *protocol.Settings, c protocol.CoordinatorService) *PlainSidecar {
	coordinatorProvider := func() (protocol.CoordinatorService, error) {
		return c, nil
	}
	return &PlainSidecar{
		settings:            settings,
		coordinatorProvider: coordinatorProvider,
	}
}

// Executes the start blocking method.
func (s *PlainSidecar) blockUntilStart() error {
	if s.startBlocker != nil {
		return s.startBlocker()
	} else {
		return nil
	}
}

/** Resolve the URL of a Resource or URL. */
func (s *PlainSidecar) resolveDestination(resource string) (string, error) {
	u, err := url.Parse(resource)
	if err != nil {
		return "", err
	}
	if s.url == nil {
		if !u.IsAbs() {
			return "", errors.New("can only resolve absolute resources in URL-less mode")
		}
		return resource, nil
	}
	full := s.url.ResolveReference(u)
	return full.String(), nil
}

func (s *PlainSidecar) makeStreamNotifyFn(id string) streamer.StreamNotifyFn {
	return func(reqId int, in int64, out int64, inFailure error, done bool) {
		coordinator, err := s.coordinatorProvider()
		if err != nil {
			log.Warn("Could not get coordinator?!")
			return
		}
		var s protocol.StatusUpdate
		var r protocol.StatusUpdateResponse
		s.Id = id
		// name and challenge is automatially added
		s.Ingress = in
		s.Outgress = out
		s.Done = done
		if inFailure != nil {
			s.Error = inFailure.Error()
		}
		err = coordinator.StatusUpdate(&s, &r)
		if err != nil {
			log.Warnf("Could not send status update to coordinator %s", err.Error())
		}
	}
}

func (s *PlainSidecar) RequestStream(req *protocol.RequestStream, res *protocol.RequestStreamResponse) error {
	err := s.blockUntilStart()
	if err != nil {
		return err
	}

	fullUrl, err := s.resolveDestination(req.Resource)
	if err != nil {
		return err
	}
	streamer, err := streamer.CreateHttpGetSource(fullUrl, req.ContentType, s.makeStreamNotifyFn(req.Id), s.settings.HttpGetRetryTime)
	if err != nil {
		res.Error = err.Error()
		return nil
	}
	log.Infof("Added a new streamer for %s to port %d", fullUrl, streamer.Port)
	s.lock.Lock()
	defer s.lock.Unlock()
	s.getStreams = append(s.getStreams, streamer)
	res.Port = streamer.Port
	return nil
}

func (s *PlainSidecar) RequestTransfer(req *protocol.RequestTransfer, res *protocol.RequestTransferResponse) error {
	err := s.blockUntilStart()
	if err != nil {
		return err
	}

	fullUrl, err := s.resolveDestination(req.Destination)
	if err != nil {
		return err
	}
	sink, err := streamer.CreateHttpPostSink(req.Source, fullUrl, req.ContentType, s.makeStreamNotifyFn(req.Id), s.settings.HttpGetRetryTime)
	if err != nil {
		res.Error = err.Error()
		return nil
	}
	log.Infof("Added new sink from %s to %s", req.Source, fullUrl)
	s.lock.Lock()
	defer s.lock.Unlock()
	s.postSinks = append(s.postSinks, sink)
	// no extra values to return
	return nil
}

func (s *PlainSidecar) RequestTransformation(req *protocol.RequestTransformation, res *protocol.RequestTransformationResponse) error {
	err := s.blockUntilStart()
	if err != nil {
		return err
	}
	fullUrl, err := s.resolveDestination(req.Destination)
	if err != nil {
		return err
	}
	transformation, err := streamer.CreateHttpPostTransformation(req.Source, fullUrl, req.ContentType, s.makeStreamNotifyFn(req.Id), s.settings.HttpGetRetryTime)
	if err != nil {
		res.Error = err.Error()
		return nil
	}
	log.Infof("Added new transformation from %s via %s to %d", req.Source, fullUrl, transformation.Port)
	s.lock.Lock()
	defer s.lock.Unlock()
	s.postTransformations = append(s.postTransformations, transformation)
	res.Port = transformation.Port
	return nil
}
