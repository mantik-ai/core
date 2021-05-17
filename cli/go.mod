module cli

require (
	github.com/golang/protobuf v1.5.2
	github.com/mattn/go-runewidth v0.0.4 // indirect
	github.com/olekukonko/tablewriter v0.0.1
	github.com/pkg/errors v0.8.1
	github.com/sirupsen/logrus v1.3.0
	github.com/stretchr/testify v1.3.0
	github.com/urfave/cli v1.22.1
	gl.ambrosys.de/mantik/go_shared v0.0.0
	golang.org/x/crypto v0.0.0-20190308221718-c2843e01d9a2
	google.golang.org/grpc v1.27.1
	google.golang.org/protobuf v1.26.0
)

replace gl.ambrosys.de/mantik/go_shared => ../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../mnp/mnpgo

go 1.13
