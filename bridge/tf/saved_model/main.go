/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package main

import (
	"github.com/mantik-ai/core/go_shared/serving/cli"
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
