module gl.ambrosys.de/mantik/go_shared

require (
	github.com/buger/jsonparser v0.0.0-20181115193947-bf1c66bbce23
	github.com/golang/protobuf v1.3.2
	github.com/nfnt/resize v0.0.0-20180221191011-83c6a9932646
	github.com/pkg/errors v0.8.1
	github.com/sirupsen/logrus v1.3.0
	github.com/stretchr/testify v1.3.0
	github.com/vmihailenco/msgpack v4.0.2+incompatible
	gl.ambrosys.de/mantik/core/mnp/mnpgo v0.0.0
	google.golang.org/appengine v1.5.0 // indirect
	google.golang.org/grpc v1.27.1
	gopkg.in/yaml.v3 v3.0.0-20190502103701-55513cacd4ae
)

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../mnp/mnpgo

go 1.13
