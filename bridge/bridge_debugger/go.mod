module bridge_debugger

require (
	github.com/Microsoft/go-winio v0.5.0 // indirect
	github.com/buger/jsonparser v1.1.1 // indirect
	github.com/containerd/containerd v1.5.0 // indirect
	github.com/docker/docker v20.10.6+incompatible
	github.com/docker/go-connections v0.4.0
	github.com/morikuni/aec v1.0.0 // indirect
	github.com/pkg/errors v0.9.1
	github.com/stretchr/testify v1.7.0
	github.com/vmihailenco/msgpack v4.0.4+incompatible // indirect
	gl.ambrosys.de/mantik/go_shared v0.0.0
	google.golang.org/appengine v1.6.7 // indirect
)

replace gl.ambrosys.de/mantik/go_shared => ../../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../../mnp/mnpgo

replace github.com/docker/docker v1.13.1 => github.com/docker/engine v0.0.0-20180816081446-320063a2ad06

go 1.16
