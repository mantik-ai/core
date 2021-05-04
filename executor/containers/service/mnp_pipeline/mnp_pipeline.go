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
package mnp_pipeline

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
)

type MnpPipeline struct {
	Steps     []MnpPipelineStep `json:"steps"`
	InputType ds.TypeReference  `json:"inputType"`
	Name      string            `json:"name"`
}

/** Returns the output type of the Pipeline. */
func (p *MnpPipeline) OutputType() ds.TypeReference {
	if len(p.Steps) == 0 {
		return p.InputType
	}
	return p.Steps[len(p.Steps)-1].OutputType
}

type MnpPipelineStep struct {
	Url        string           `json:"url"`
	OutputType ds.TypeReference `json:"outputType"`
}
