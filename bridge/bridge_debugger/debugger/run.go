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
	"context"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/mount"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/fileutils"
	"github.com/docker/go-connections/nat"
	"gl.ambrosys.de/mantik/go_shared/util/dirzip"
	"io"
	"io/ioutil"
	"os"
	"os/signal"
	"path"
	"path/filepath"
	"syscall"
	"time"
)

func RunBridge(imageName string, mantikHeader string, dataZipFile string, keepDataDir bool) {
	ctx := context.Background()
	cli, err := client.NewClientWithOpts(client.FromEnv)
	ExitOnError(err, "Creating Docker client")
	tempDir := prepareTempDirectory(mantikHeader, dataZipFile)
	if !keepDataDir {
		defer deleteTempDir(tempDir)
	}

	createResponse, err := cli.ContainerCreate(ctx, &container.Config{
		Image:     imageName,
		Tty:       true,
		OpenStdin: false,
		ExposedPorts: nat.PortSet{
			"8502/tcp": struct{}{},
		},
	}, &container.HostConfig{
		GroupAdd: []string{"1000"},
		Mounts: []mount.Mount{
			mount.Mount{
				Type:   mount.TypeBind,
				Source: tempDir,
				Target: "/data",
			},
		},
		PortBindings: nat.PortMap{
			"8502/tcp": []nat.PortBinding{
				{
					HostIP:   "127.0.0.1",
					HostPort: "8502",
				},
			},
		},
	}, nil, nil, "mantik_bridge")

	ExitOnError(err, "Container Create")

	registerContainerStopSignal(cli, createResponse.ID)
	err = cli.ContainerStart(
		ctx,
		createResponse.ID,
		types.ContainerStartOptions{},
	)

	println("Container Started...")

	logs, err := cli.ContainerLogs(ctx, createResponse.ID, types.ContainerLogsOptions{
		ShowStderr: true,
		ShowStdout: true,
		Follow:     true,
	})
	ExitOnError(err, "Getting container logs")
	go func() {
		io.Copy(os.Stdout, logs)
	}()

	statusCode, err := waitContainer(cli, createResponse.ID)
	ExitOnError(err, "Waiting for Container")
	if statusCode != 0 {
		println("Container Returned status code", statusCode)
	}

	err = cli.ContainerRemove(ctx, createResponse.ID, types.ContainerRemoveOptions{})
	ExitOnError(err, "Removing Container")
}

func waitContainer(cli *client.Client, id string) (int64, error) {
	okChan, errChan := cli.ContainerWait(context.Background(), id, container.WaitConditionNotRunning)
	select {
	case err := <-errChan:
		return 0, err
	case s := <-okChan:
		return s.StatusCode, nil
	}
}

func registerContainerStopSignal(cli *client.Client, id string) {
	c := make(chan os.Signal)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-c
		println("SIGTERM catched, stopping container...")
		timeout := time.Second * 10
		_, err := cli.ContainerInspect(context.Background(), id)
		if err == nil {
			cli.ContainerStop(context.Background(), id, &timeout)
		} else {
			println("Cannot inspect container, already done?!")
		}
	}()
}

func deleteTempDir(tempDir string) {
	os.RemoveAll(tempDir)
}

func prepareTempDirectory(mantikHeaderPath string, dataZipFile string) string {
	tempDir, err := ioutil.TempDir("", "mantik_bridge_debugger")
	ExitOnError(err, "Creating Temp directory")
	println("Preparing Directory ", tempDir)

	_, err = fileutils.CopyFile(mantikHeaderPath, path.Join(tempDir, "MantikHeader"))
	ExitOnError(err, "Copy MantikHeader")

	dataDestinationDir := path.Join(tempDir, "payload")

	if len(dataZipFile) > 0 {
		println("Preparing Data directory")
		err = dirzip.UnzipDiectory(dataZipFile, dataDestinationDir, true)
		ExitOnError(err, "Unzipping Data Dir")
	} else {
		println("Skipping data directory as no zip file given")
	}
	// Bridges have GID 1000
	err = chownr(tempDir, -1, 1000)
	ExitOnError(err, "Changing group")
	err = chmodr(tempDir, 0770)
	ExitOnError(err, "Changing chmod")
	return tempDir
}

// Changes the uid/gid of a directory recursively, ids of -1 mean no change.
func chownr(dir string, userId int, groupId int) error {
	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err == nil {
			err = os.Chown(path, userId, groupId)
		}
		return err
	})
}

func chmodr(dir string, mode os.FileMode) error {
	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err == nil {
			err = os.Chmod(path, mode)
		}
		return err
	})
}
