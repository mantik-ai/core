package debugger

import (
	"context"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/mount"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/fileutils"
	"github.com/docker/go-connections/nat"
	"gl.ambrosys.de/mantik/go_shared/serving"
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

func RunBridge(imageName string, mantikfile string, dataZipFile string, keepDataDir bool) {
	ctx := context.Background()
	cli, err := client.NewClientWithOpts(client.FromEnv)
	ExitOnError(err, "Creating Docker client")
	tempDir := prepareTempDirectory(mantikfile, dataZipFile)
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
	}, nil, "mantik_bridge")

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

func prepareTempDirectory(mantikfilePath string, dataZipFile string) string {
	tempDir, err := ioutil.TempDir("", "mantik_bridge_debugger")
	ExitOnError(err, "Creating Temp directory")
	println("Preparing Directory ", tempDir)

	_, err = fileutils.CopyFile(mantikfilePath, path.Join(tempDir, "Mantikfile"))
	ExitOnError(err, "Copy Mantikfile")

	mf, err := serving.LoadMantikfile(mantikfilePath)
	ExitOnError(err, "Loading Mantikfile")

	var dataDestinationDir string
	if mf.Directory() != nil {
		dataDestinationDir = path.Join(tempDir, *mf.Directory())
	} else {
		dataDestinationDir = tempDir
	}

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
