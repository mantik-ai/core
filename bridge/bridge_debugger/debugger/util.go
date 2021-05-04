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
package debugger

import (
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"io"
	"os"
)

func ExitOnError(err error, op string) {
	if err != nil {
		println("Fatal Error on ", op, err.Error())
		os.Exit(1)
	}
}

func readBundleFromFile(inputFile string) (*element.Bundle, error) {
	file, err := os.Open(inputFile)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	bundle, err := natural.DecodeBundleFromReader(serializer.BACKEND_MSGPACK, file)
	if err != nil {
		return nil, err
	}
	return bundle, err
}

func writeBundleToFile(bundle *element.Bundle, outputFile string) error {
	file, err := os.OpenFile(outputFile, os.O_WRONLY|os.O_CREATE, 0660)
	if err != nil {
		return err
	}
	defer file.Close()
	err = natural.EncodeBundleToWriter(bundle, serializer.BACKEND_MSGPACK, file)
	return err
}

func writeStreamToFile(reader io.ReadCloser, fileName string) error {
	file, err := os.OpenFile(fileName, os.O_WRONLY|os.O_CREATE, 0660)
	if err != nil {
		return err
	}
	defer file.Close()
	defer reader.Close()
	_, err = io.Copy(file, reader)
	return err
}
