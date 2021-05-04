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
package actions

import (
	"cli/client"
	"fmt"
	"github.com/sirupsen/logrus"
)

// Show version Arguments
type VersionArguments struct {
}

func PrintVersion(engineClient *client.EngineClient, args *VersionArguments, appVersion string) {
	version, err := engineClient.Version()
	if err != nil {
		logrus.Fatal("Could not connect", err.Error())
	}
	fmt.Println("Engine Version ", version.Version)
	fmt.Println("Client Version ", appVersion)
	state, err := readLoginState()
	if err == nil {
		fmt.Println("Logged in Remote Registry")
		fmt.Printf("  URL  : %s\n", state.Url)
		fmt.Printf("  Token: %s\n", state.Token)
		if state.ValidUntil != nil {
			fmt.Printf("  Till : %s\n", state.ValidUntil.String())
		}
	}
}
