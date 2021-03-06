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
	"cli/protos/mantik/engine"
	"context"
	"time"
)

// Arguments for Login command
type LoginArguments struct {
	// url (may be empty to use default)
	Url      string
	Username string
	Password string
}

// Arguments for Logout command
type LogoutArguments struct {
	// empty
}

func Login(client *client.EngineClient, args *LoginArguments) error {
	response, err := client.RemoteRegistry.Login(context.Background(), &engine.LoginRequest{
		Credentials: &engine.LoginCredentials{
			Url:      args.Url,
			Username: args.Username,
			Password: args.Password,
		},
	})
	if err != nil {
		return err
	}

	var validUntil *time.Time
	if response.ValidUntil != nil {
		x := time.Unix(
			response.ValidUntil.Seconds,
			int64(response.ValidUntil.Nanos),
		)
		validUntil = &x
	}

	state := LoginState{
		Url:        response.Token.Url,
		Token:      response.Token.Token,
		ValidUntil: validUntil,
	}

	return storeLoginState(&state)
}

func Logout(engineClient *client.EngineClient, args *LogoutArguments) error {
	// there is not much to do as deleting the file
	return removeLoginState()
}
