package streamer

import (
	"coordinator/service/progressutil"
	log "github.com/sirupsen/logrus"
	"net"
	"time"
)

/* Stream an HTTP Resource as a TCP Port. */
type HttpGetSource struct {
	Port          int
	Url           string
	listener      net.Listener
	notifyFn      StreamNotifyFn
	nextRequestId int
	contentType   *string
	retryInterval time.Duration
}

/* Fetch data from HTTP Get and present it on a TCP Socket on the fly. */
func CreateHttpGetSource(url string, contentType *string, notifyFn StreamNotifyFn, retryInterval time.Duration) (*HttpGetSource, error) {
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
	r.retryInterval = retryInterval
	log.Debugf("Created http get source from %s at port %d", url, r.Port)
	go r.accept()
	return &r, nil
}

func (s *HttpGetSource) accept() {
	for {
		conn, err := s.listener.Accept()
		if err != nil {
			log.Warnf("Error on accepting TCP Connection %s", err.Error())
			return
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
	stream, err := OpenHttpStream(s.Url, s.retryInterval, s.contentType)
	if err != nil {
		s.notifyFn(requestId, 0, 0, err, false)
		c.Close()
		return
	}

	defer stream.Close()
	defer c.Close()
	progressutil.CopyAndNotify(StreamNotificationDelay, stream, c, func(bytes int64, failed error, done bool) {
		s.notifyFn(requestId, 0, bytes, failed, done)
	})
}
