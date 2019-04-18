package main

import (
	"gl.ambrosys.de/mantik/go_shared/serving/cli"
	"os"
	"tfbridge/services/tfadapter"
)

func main() {
	var tfBackend tfadapter.TensorflowBackend
	cli.Start(os.Args, &tfBackend)
}
