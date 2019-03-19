package streamer

import (
	"coordinator/service/progressutil"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net"
	"net/http"
)

/* Stream an HTTP Resource as a TCP Port. */
type HttpGetSource struct {
	Port          int
	Url           string
	listener      net.Listener
	notifyFn      StreamNotifyFn
	nextRequestId int
	contentType   *string
}

/* Fetch data from HTTP Get and present it on a TCP Socket on the fly. */
func CreateHttpGetSource(url string, contentType *string, notifyFn StreamNotifyFn) (*HttpGetSource, error) {
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		return nil, err
	}
	tcpAddr := listener.Addr().(*net.TCPAddr)
	var r HttpGetSource
	r.Port = tcpAddr.Port
	r.Url = url
	r.notifyFn = notifyFn
	r.listener = listener
	r.contentType = contentType
	log.Debugf("Created http get source from %s at port %d", url, r.Port)
	go r.accept()
	return &r, nil
}

func (s *HttpGetSource) accept() {
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

func (s *HttpGetSource) Close() {
	s.listener.Close()
}

func (s *HttpGetSource) serveConnection(requestId int, c net.Conn) {
	req, err := http.NewRequest(http.MethodGet, s.Url, nil)
	if s.contentType != nil {
		req.Header.Add(HttpAccept, *s.contentType)
	}
	if err != nil {
		log.Warnf("Could not create HTTP request %s", err.Error())
		s.notifyFn(requestId, 0, 0, err, false)
		return
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		log.Warnf("Got an error on HTTP Get %s", err.Error())
		s.notifyFn(requestId, 0, 0, err, false)
		return
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		log.Warnf("Got %d Response on HTTP GET %s", resp.StatusCode, s.Url)
		c.Close()
		err = errors.Errorf("Got %d response on HTTP GET", resp.StatusCode)
		s.notifyFn(requestId, 0, 0, err, false)
		return
	}
	if resp.StatusCode == 204 {
		log.Warn("Empty 204 Response on HTTP GET")
		c.Close()
		s.notifyFn(requestId, 0, 0, nil, true)
		return
	}
	defer resp.Body.Close()
	defer c.Close()
	progressutil.CopyAndNotify(StreamNotificationDelay, resp.Body, c, func(bytes int64, failed error, done bool) {
		s.notifyFn(requestId, 0, bytes, failed, done)
	})
}
