package main

import (
	"binary/services/binaryadapter"
	"gl.ambrosys.de/mantik/go_shared/serving/cli"
	"os"
)

func main() {
	var backend = binaryadapter.BinaryBackend{}
	cli.Start(os.Args, &backend)
}
