package main

import (
	"gl.ambrosys.de/mantik/go_shared/serving/cli"
	"os"
	"tfbridge/services/tfadapter"
)

func main() {
	if len(os.Args) > 2 && os.Args[1] == "direct_analyze" {
		// enhancement, directly analyze an export without serving or MantikHeader
		dir := os.Args[2]
		model, err := tfadapter.LoadModel(dir)
		if err != nil {
			println("Error ", err.Error())
			os.Exit(1)
		}
		println("Analyze Result")
		println(model.AnalyzeResult.AsJson())
		println("Algorithm Type")
		println(model.AlgorithmType.AsJson())
		os.Exit(0)
	}

	var tfBackend tfadapter.TensorflowBackend
	cli.Start(os.Args, &tfBackend)
}
