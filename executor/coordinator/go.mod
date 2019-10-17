module coordinator

require (
	github.com/pkg/errors v0.8.1
	github.com/sirupsen/logrus v1.3.0
	github.com/stretchr/testify v1.3.0
	gl.ambrosys.de/mantik/go_shared v0.0.0
)

replace gl.ambrosys.de/mantik/go_shared => ../../go_shared

go 1.13
