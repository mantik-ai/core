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
package adapter

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

func TestHelloWorld(t *testing.T) {
	csv, err := LoadModelFromDirectory("../examples/01_helloworld")
	assert.NoError(t, err)

	elements, err := element.ReadAllFromStreamReader(csv.Get())
	assert.NoError(t, err)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow("Alice", int32(42)),
		builder.PrimitiveRow("Bob", int32(13)),
		builder.PrimitiveRow("Charly", int32(14)),
		builder.PrimitiveRow("Dave", int32(15)),
		builder.PrimitiveRow("Emi\"l", int32(16)),
	)

	assert.Equal(t, expectedOutput, elements)
}

func TestEmpty(t *testing.T) {
	csv, err := LoadModelFromDirectory("../examples/02_empty")
	assert.NoError(t, err)

	elements, err := element.ReadAllFromStreamReader(csv.Get())
	assert.NoError(t, err)

	assert.Empty(t, elements)
}

func TestCustomized(t *testing.T) {
	csv, err := LoadModelFromDirectory("../examples/03_customized")
	assert.NoError(t, err)

	elements, err := element.ReadAllFromStreamReader(csv.Get())
	assert.NoError(t, err)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow("Alice", int32(100)),
		builder.PrimitiveRow("Bob", int32(200)),
		builder.PrimitiveRow("Charly", int32(300)),
	)

	assert.Equal(t, expectedOutput, elements)
}

func TestCities(t *testing.T) {
	// Sample is from here: https://people.sc.fsu.edu/~jburkardt/data/csv/csv.html
	csv, err := LoadModelFromDirectory("../examples/04_cities")
	assert.NoError(t, err)
	got, err := element.ReadAllFromStreamReader(csv.Get())
	assert.NoError(t, err)
	assert.Equal(t, 128, len(got))
}

/*
func TestFromUrl(t *testing.T) {
	// Sample is from here: https://people.sc.fsu.edu/~jburkardt/data/csv/csv.html
	// Do not activate test outside of development
	csv, err := LoadModelFromDirectory("../examples/05_url")
	assert.NoError(t, err)
	got, err := element.ReadAllFromStreamReader(csv.Get())
	assert.NoError(t, err)
	assert.Equal(t, 128, len(got))
}
*/
