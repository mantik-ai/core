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
)

// Deploy a Mantik Item
type DeployArguments struct {
	MantikId    string
	IngressName string
	NameHint    string
}

func Deploy(engine *client.EngineClient, deploy *DeployArguments) error {
	fmt.Println("Deploying", deploy.MantikId, "...")
	item, err := engine.Items.Get(deploy.MantikId)
	if err != nil {
		return err
	}
	response, err := item.Deploy(deploy.IngressName, deploy.NameHint)
	if err != nil {
		return err
	}
	fmt.Printf("Deployed Item %s\n", deploy.MantikId)
	fmt.Printf("External Url: %s\n", formatOptionalString(response.ExternalUrl))
	fmt.Printf("Internal Url: %s\n", formatOptionalString(response.InternalUrl))
	fmt.Printf("Name:         %s\n", formatOptionalString(response.Name))
	return nil
}
