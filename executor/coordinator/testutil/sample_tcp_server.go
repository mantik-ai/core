package testutil

import (
	"fmt"
	"io"
	"io/ioutil"
	"net"
)

type SampleTcpSource struct {
	l        net.Listener
	Port     int
	Requests int
	reader   io.Reader
}

func CreateSampleTcpSource(reader io.Reader) *SampleTcpSource {
	var r SampleTcpSource
	var err error
	r.l, err = net.Listen("tcp", "localhost:0")
	if err != nil {
		panic(err)
	}
	r.Port = r.l.Addr().(*net.TCPAddr).Port
	r.reader = reader

	go r.run()

	return &r
}

func (s *SampleTcpSource) Close() {
	s.l.Close()
}

func (s *SampleTcpSource) FullAddress() string {
	return fmt.Sprintf("localhost:%d", s.Port)
}

func (s *SampleTcpSource) run() {
	for {
		c, err := s.l.Accept()
		s.Requests++
		if err != nil {
			return
		}
		io.Copy(c, s.reader)
		c.Close()
	}
}

func TcpPullData(address string) ([]byte, error) {
	resp, err := net.Dial("tcp", address)
	if err != nil {
		return nil, err
	}
	defer resp.Close()
	result, err := ioutil.ReadAll(resp)
	return result, err
}
