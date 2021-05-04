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
package cmd

import (
	"cli/client"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestParseArguments_Global(t *testing.T) {
	args, err := ParseArguments([]string{"app"}, "")
	assert.NoError(t, err)
	assert.Equal(t, client.DefaultPort, args.ClientArgs.Port)
	assert.Equal(t, client.DefaultHost, args.ClientArgs.Host)
	assert.False(t, args.Debug)
}

func TestParseArguments_GlobalOverride(t *testing.T) {
	args, err := ParseArguments([]string{"app", "--host", "foo", "--port", "1234", "--debug"}, "")
	assert.NoError(t, err)
	assert.Equal(t, 1234, args.ClientArgs.Port)
	assert.Equal(t, "foo", args.ClientArgs.Host)
	assert.True(t, args.Debug)
}

func TestParseArguments_About(t *testing.T) {
	args, err := ParseArguments([]string{"app", "version"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Version)
}

func TestParseArguments_Items(t *testing.T) {
	args, err := ParseArguments([]string{"app", "items", "--deployed", "--kind", "dataset", "--all", "--deployed", "--noTable"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Items)
	assert.Equal(t, true, args.Items.Anonymous)
	assert.Equal(t, true, args.Items.NoTable)
	assert.Equal(t, "dataset", args.Items.Kind)
	assert.Equal(t, true, args.Items.Deployed)
}

func TestParseArguments_Item(t *testing.T) {
	args, err := ParseArguments([]string{"app", "item", "foobar"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Item)
	assert.Equal(t, "foobar", args.Item.MantikId)

	_, err = ParseArguments([]string{"app", "item"}, "")
	assert.Equal(t, MissingArgument, err)
}

func TestParseArguments_Tag(t *testing.T) {
	args, err := ParseArguments([]string{"app", "tag", "old", "new"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Tag)
	assert.Equal(t, "old", args.Tag.MantikId)
	assert.Equal(t, "new", args.Tag.NewMantikId)

	_, err = ParseArguments([]string{"app", "tag", "onlyOne"}, "")
	assert.Equal(t, MissingArgument, err)
}

func TestParseArguments_Deploy(t *testing.T) {

	args, err := ParseArguments([]string{"app", "deploy", "foobar", "--ingress", "ingress1", "--nameHint", "name1"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Deploy)
	assert.Equal(t, "foobar", args.Deploy.MantikId)
	assert.Equal(t, "ingress1", args.Deploy.IngressName)
	assert.Equal(t, "name1", args.Deploy.NameHint)

	_, err = ParseArguments([]string{"app", "deploy"}, "")
	assert.Equal(t, MissingArgument, err)
}

func TestParseArguments_Add(t *testing.T) {
	args, err := ParseArguments([]string{"app", "add", "--name", "foobar", "dir1"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Add)
	assert.Equal(t, "foobar", args.Add.NamedMantikId)
	assert.Equal(t, "dir1", args.Add.Directory)

	_, err = ParseArguments([]string{"app", "add"}, "")
	assert.Equal(t, MissingArgument, err)
}

func TestParseArguments_Extract(t *testing.T) {
	args, err := ParseArguments([]string{"app", "extract", "-o", "my_directory", "mantikid1"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Extract)
	assert.Equal(t, "my_directory", args.Extract.Directory)
	assert.Equal(t, "mantikid1", args.Extract.MantikId)

	_, err = ParseArguments([]string{"app", "extract", "-o", "foo"}, "")
	assert.Equal(t, MissingArgument, err)

	// Missing directory
	_, err = ParseArguments([]string{"app", "extract", "mantikId"}, "")
	assert.Error(t, err)
}

func TestParseLogin(t *testing.T) {
	args, err := ParseArguments([]string{"app", "login", "--url", "http://foo", "-u", "user1", "-p", "password1"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Login)
	assert.Equal(t, "http://foo", args.Login.Url)
	assert.Equal(t, "user1", args.Login.Username)
	assert.Equal(t, "password1", args.Login.Password)
}

func TestParseLogout(t *testing.T) {
	args, err := ParseArguments([]string{"app", "logout"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Logout)
}

func TestParsePull(t *testing.T) {
	args, err := ParseArguments([]string{"app", "pull", "item1"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Pull)
	assert.Equal(t, "item1", args.Pull.MantikId)

	// Missing id
	_, err = ParseArguments([]string{"app", "pull"}, "")
	assert.Error(t, err)
}

func TestParsePush(t *testing.T) {
	args, err := ParseArguments([]string{"app", "push", "item1"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Push)
	assert.Equal(t, "item1", args.Push.MantikId)

	// Missing id
	_, err = ParseArguments([]string{"app", "push"}, "")
	assert.Error(t, err)
}
