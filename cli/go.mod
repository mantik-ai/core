module cli

require (
	github.com/buger/jsonparser v1.1.1 // indirect
	github.com/cpuguy83/go-md2man/v2 v2.0.0 // indirect
	github.com/golang/protobuf v1.5.2
	github.com/mattn/go-runewidth v0.0.12 // indirect
	github.com/olekukonko/tablewriter v0.0.5
	github.com/pkg/errors v0.9.1
	github.com/rivo/uniseg v0.2.0 // indirect
	github.com/russross/blackfriday/v2 v2.1.0 // indirect
	github.com/sirupsen/logrus v1.8.1
	github.com/stretchr/testify v1.7.0
	github.com/urfave/cli v1.22.5
	github.com/vmihailenco/msgpack v4.0.4+incompatible // indirect
	gl.ambrosys.de/mantik/go_shared v0.0.0
	golang.org/x/crypto v0.0.0-20210506145944-38f3c27a63bf
	golang.org/x/term v0.0.0-20210503060354-a79de5458b56 // indirect
	google.golang.org/appengine v1.6.7 // indirect
	google.golang.org/grpc v1.37.0
	google.golang.org/protobuf v1.26.0
)

replace gl.ambrosys.de/mantik/go_shared => ../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../mnp/mnpgo

go 1.16
