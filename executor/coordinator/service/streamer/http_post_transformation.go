package streamer

import (
	"coordinator/service/progressutil"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"io"
	"net"
	"net/http"
	"sync"
)

/*
A HTTP Post Transformation is a stateless process, which pipes data from a source through a HTTP Post Request
and servers the resulting stream as TCP Socket again
*/
type HttpPostTransformation struct {
	Port          int
	url           string
	listener      net.Listener
	fn            StreamNotifyFn
	nextRequestId int
	from          string
	contentType   *string
}

func CreateHttpPostTransformation(from string, url string, contentType *string, notifyFn StreamNotifyFn) (*HttpPostTransformation, error) {
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		return nil, err
	}
	tcpAddr := listener.Addr().(*net.TCPAddr)
	var r HttpPostTransformation
	r.Port = tcpAddr.Port
	r.url = url
	r.fn = notifyFn
	r.listener = listener
	r.from = from
	r.contentType = contentType
	log.Debugf("Created transformation process from %s to %s on port %d", from, url, r.Port)
	go r.accept()
	return &r, nil
}

func (s *HttpPostTransformation) accept() {
	for {
		conn, err := s.listener.Accept()
		if err != nil {
			log.Warnf("Error on accepting TCP Connection %s", err.Error())
		}
		requestId := s.nextRequestId
		s.nextRequestId++
		go s.serveConnection(requestId, conn)
	}
}

func (s *HttpPostTransformation) Close() {
	s.listener.Close()
}

func (s *HttpPostTransformation) serveConnection(requestId int, c net.Conn) {
	fromConnection, err := net.Dial("tcp", s.from)
	defer fromConnection.Close()
	defer c.Close()

	if err != nil {
		log.Warnf("Could not request from resource %s", s.from)
		s.fn(0, 0, 0, err, false)
		return
	}

	// TODO It's currently not possible (at least for the server in Go) that we
	// can consume and forward the response before we completely send the request to the server.
	// Ticket: https://github.com/golang/go/issues/15527

	wrappedFrom := progressutil.MakeProgressReader(fromConnection)

	var wrappedResponse *progressutil.ProgressReader
	var progressMux sync.Mutex // protecting wrappedResponse

	notifier := progressutil.GeneratePeriodicCallback(StreamNotificationDelay, func() {
		ix := wrappedFrom.Bytes()
		var ox int64
		progressMux.Lock()
		if wrappedResponse != nil {
			ox = wrappedResponse.Bytes()
		}
		progressMux.Unlock()
		s.fn(requestId, ix, ox, nil, false)
	})

	req, err := http.NewRequest(http.MethodPost, s.url, wrappedFrom)
	if err != nil {
		log.Warnf("Could not create POST request to %s, %s", s.url, err.Error())
		notifier.Stop()
		s.fn(requestId, 0, 0, err, false)
		return
	}
	if s.contentType != nil {
		req.Header.Add(HttpContentType, *s.contentType)
	}

	// Hier liegt das Problem, der HTTP Request bekommt nicht den vollen Saft
	response, err := http.DefaultClient.Do(req)
	progressMux.Lock()
	wrappedResponse = progressutil.MakeProgressReader(response.Body)
	progressMux.Unlock()

	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		log.Warnf("Received bad status code %d", response.StatusCode)
		err = errors.Errorf("Received status code %d", response.StatusCode)
		notifier.Stop()
		s.fn(requestId, wrappedFrom.Bytes(), wrappedResponse.Bytes(), err, false)
		return
	}

	_, err = io.Copy(c, wrappedResponse)
	notifier.Stop()

	bytes, isReady := wrappedResponse.BytesAndReady()
	if !isReady {
		log.Warnf("Target has not consumed all bytes, only %d", bytes)
	}
	if err != nil {
		log.Warnf("Could not forward all bytes %s", err.Error())
		s.fn(requestId, wrappedFrom.Bytes(), wrappedResponse.Bytes(), err, false)
		return
	}
	s.fn(requestId, wrappedFrom.Bytes(), wrappedResponse.Bytes(), nil, true)
}
