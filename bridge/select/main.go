package main

import (
	"gl.ambrosys.de/mantik/go_shared/serving/cli"
	"os"
	"select/services/selectbridge"
)

func main() {
	var backend selectbridge.SelectBackend
	cli.Start(os.Args, &backend)
}
