module github.com/mantik-ai/core/go_shared

require (
	github.com/buger/jsonparser v0.0.0-20181115193947-bf1c66bbce23
	github.com/golang/protobuf v1.5.2
	github.com/nfnt/resize v0.0.0-20180221191011-83c6a9932646
	github.com/pkg/errors v0.9.1
	github.com/sirupsen/logrus v1.8.1
	github.com/stretchr/testify v1.7.0
	github.com/vmihailenco/msgpack v4.0.2+incompatible
	github.com/mantik-ai/core/mnp/mnpgo v0.0.0
	google.golang.org/appengine v1.5.0 // indirect
	google.golang.org/protobuf v1.26.0
	gopkg.in/yaml.v3 v3.0.0-20210107192922-496545a6307b
)

replace github.com/mantik-ai/core/mnp/mnpgo => ../mnp/mnpgo

go 1.16
