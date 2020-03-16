package mnpgo

import (
	"fmt"
	"github.com/pkg/errors"
	"net/url"
	"path"
	"strconv"
)

type MnpUrl struct {
	// Hostname:HostPort
	Address string
	// MNP SessionId
	SessionId string
	// MNP Port
	Port int
}

func (u *MnpUrl) String() string {
	return fmt.Sprintf("mnp://%s/%s/%d", u.Address, u.SessionId, u.Port)
}

func ParseMnpUrl(mnpUrl string) (*MnpUrl, error) {
	parsed, err := url.Parse(mnpUrl)
	if err != nil {
		return nil, err
	}
	if parsed.Scheme != "mnp" {
		return nil, errors.New("Unexpected scheme")
	}
	var result MnpUrl
	result.Address = parsed.Host
	pre, portString := path.Split(parsed.Path)
	pre, sessionId := path.Split(path.Clean(pre))
	if pre != "/" {
		return nil, errors.New("Bad path")
	}
	port, err := strconv.Atoi(portString)
	if err != nil {
		return nil, err
	}
	result.SessionId = sessionId
	result.Port = port

	return &result, nil
}
