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
	"encoding/json"
	"github.com/pkg/errors"
	"io/ioutil"
	"os"
	"path"
	"time"
)

type LoginState struct {
	Url        string     `json:"url"`
	Token      string     `json:"token"`
	ValidUntil *time.Time `json:"validUntil"`
}

// Remove a login state, if there is one
func removeLoginState() error {
	file := loginStateFile()
	return os.Remove(file) // ignore result
}

// Store the login state
func storeLoginState(state *LoginState) error {
	file := loginStateFile()
	err := os.MkdirAll(path.Dir(file), os.ModePerm)
	if err != nil {
		return errors.Wrap(err, "Could not ensure config file directory")
	}
	serialized, err := json.Marshal(state)
	if err != nil {
		return errors.Wrap(err, "Could not serialize login state")
	}
	err = ioutil.WriteFile(file, serialized, os.ModePerm)
	if err != nil {
		return errors.Wrap(err, "Could not write config file")
	}
	return nil
}

// Read the login state. If the file is not existing null is returned
func readLoginState() (*LoginState, error) {
	file := loginStateFile()
	content, err := ioutil.ReadFile(file)
	if err != nil {
		return nil, err
	}
	var state LoginState
	err = json.Unmarshal(content, &state)
	if err != nil {
		return nil, err
	}
	if state.ValidUntil.Before(time.Now()) {
		return nil, errors.New("Login not valid anymore")
	}
	return &state, nil
}

// Returns the path to the login state file
func loginStateFile() string {
	dir, err := os.UserConfigDir()
	if err != nil {
		panic(err.Error())
	}
	return path.Join(dir, "mantikcli", "login")
}
