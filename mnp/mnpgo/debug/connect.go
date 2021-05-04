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
	"flag"
	"fmt"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/client"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/debug/echo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/grpchttpproxy"
	server2 "gl.ambrosys.de/mantik/core/mnp/mnpgo/server"
	"os"
)

var usage = `
MNPGO Debug Utility

Usage:

connect <Address> Connect to MNP Go URL and call about
echo              Starts an MNP Echo Server
help              Print help
`

func main() {
	if len(os.Args) < 2 {
		println(usage)
		os.Exit(0)
	}

	connectOptions := flag.NewFlagSet("connect", flag.ExitOnError)
	connectProxy := connectOptions.String("proxy", "", "Proxy Url")

	echoOptions := flag.NewFlagSet("echo", flag.ExitOnError)
	echoPort := echoOptions.Int("port", 8502, "Listening Port")

	cmd := os.Args[1]
	switch cmd {
	case "help":
		println(usage)
		os.Exit(0)
	case "connect":
		connectOptions.Parse(os.Args[2:])
		if connectOptions.NArg() < 1 {
			println("Expected address")
			os.Exit(1)
		}
		address := connectOptions.Arg(0)
		var proxySettings *grpchttpproxy.ProxySettings
		var err error
		if len(*connectProxy) > 0 {
			proxySettings, err = grpchttpproxy.NewProxySettings(*connectProxy)
			if err != nil {
				println("Error parsing proxy", err.Error())
				os.Exit(1)
			}
		}
		err = connectTest(address, proxySettings)
		if err != nil {
			println("Error", err)
			os.Exit(1)
		}
		os.Exit(0)
	case "echo":
		echoOptions.Parse(os.Args[2:])
		err := echoServer(*echoPort)
		if err != nil {
			println("Error", err)
			os.Exit(1)
		}
		os.Exit(0)
	default:
		println("Unknown command", cmd)
		os.Exit(1)
	}
}

func connectTest(address string, proxy *grpchttpproxy.ProxySettings) error {
	c, err := client.ConnectClientWithProxy(address, proxy)
	if err != nil {
		return errors.Wrap(err, "Could not connect")
	}
	about, err := c.About()
	if err != nil {
		return errors.Wrap(err, "Could not send about call")
	}
	println("About response", about.Name)
	err = c.Close()
	if err != nil {
		return errors.Wrap(err, "Could not close connection")
	}
	return nil
}

func echoServer(port int) error {
	handler := echo.EchoHandler{}
	server := server2.NewServer(&handler)
	err := server.Listen(fmt.Sprintf("0.0.0.0:%d", port))
	if err != nil {
		return err
	}
	return server.Serve()
}
