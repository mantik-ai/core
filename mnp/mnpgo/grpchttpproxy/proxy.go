package grpchttpproxy

import "net/url"

type ProxySettings struct {
	Url       *url.URL
	UserAgent string
}

func NewProxySettings(urlString string) (*ProxySettings, error) {
	u, err := url.Parse(urlString)
	if err != nil {
		return nil, err
	}
	return &ProxySettings{
		Url:       u,
		UserAgent: "mnpgo",
	}, nil
}
