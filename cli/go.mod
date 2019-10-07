module cli

require (
	github.com/golang/protobuf v1.3.2
	github.com/mattn/go-runewidth v0.0.4 // indirect
	github.com/olekukonko/tablewriter v0.0.1
	github.com/sirupsen/logrus v1.3.0
	github.com/stretchr/testify v1.3.0 // indirect
	google.golang.org/grpc v1.23.1
)

replace gl.ambrosys.de/mantik/go_shared => ../go_shared

go 1.13
