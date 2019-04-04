package main

import (
	"archive/zip"
	"bytes"
	"encoding/base64"
	"flag"
	"fmt"
	"github.com/pkg/errors"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"strings"
	"syscall"
)

const RcInvalidArgs = 1
const RcCouldNotCreateDirectory = 2
const RcInvalidMantikFile = 3
const RcCouldNotWriteMantikFile = 4
const RcCouldNotReadUrl = 5
const RcCouldNotUnzip = 6
const FilePrefix = "file://"
const TempFile = "download.temp"

/* Prepares the /data directory by unziping a file given as URL. Also places Mantikfile at the correct place. */
func main() {
	options := flag.NewFlagSet("preparer", flag.ExitOnError)
	url := options.String("url", "", "Url to download")
	dir := options.String("dir", "/data", "Destination Directory")
	payloadSubDir := options.String("pdir", "", "Optional Payload Sub directory")
	mantikfile := options.String("mantikfile", "", "Mantikfile to add (Base64 encoded)")

	err := options.Parse(os.Args[1:])
	if err != nil {
		os.Exit(RcInvalidArgs)
	}
	if len(*dir) == 0 {
		println("Empty directory given")
		os.Exit(RcInvalidArgs)
	}
	if len(*url) == 0 {
		println("Empty URL")
		os.Exit(RcInvalidArgs)
	}

	// For Debugging file permissions
	uid := os.Getuid()
	gid := os.Getgid()
	fmt.Printf("Current user: %d group: %d\n", uid, gid)

	var parsedMantikfile []byte
	if len(*mantikfile) > 0 {
		decoder := base64.NewDecoder(base64.StdEncoding, bytes.NewBufferString(*mantikfile))
		parsed, err := ioutil.ReadAll(decoder)
		if err != nil {
			println("Could not parse Mantikfile", err.Error())
			os.Exit(RcInvalidMantikFile)
		}
		parsedMantikfile = parsed
	}
	err = os.MkdirAll(*dir, 0755)
	if err != nil {
		println("Could not create directory", err.Error())
		os.Exit(RcCouldNotCreateDirectory)
	}
	println("Ensured ", *dir)
	if parsedMantikfile != nil {
		err := ioutil.WriteFile(path.Join(*dir, "Mantikfile"), parsedMantikfile, 0644)
		if err != nil {
			println("Could not write Mantikfile", err.Error())
			os.Exit(RcCouldNotWriteMantikFile)
		}
		println("Wrote Mantikfile...")
	}

	payload, err := retrieveUrl(*url)
	if err != nil {
		println("Could not read URL", err.Error())
		os.Exit(RcCouldNotReadUrl)
	}

	var payloadDir string = *dir
	if len(*payloadSubDir) > 0 {
		payloadDir = path.Join(*dir, *payloadSubDir)
		// It is import that the created directory is writeable for the same group, as bridges are allowed to write there
		// default umask is 0002, which will make it non-group-writeable.
		oldUmask := syscall.Umask(02)
		err := os.MkdirAll(payloadDir, os.ModePerm) // note: the bridge container may also write into it and is of same group
		syscall.Umask(oldUmask)
		if err != nil {
			println("Could not create payload sub directory")
			os.Exit(RcCouldNotCreateDirectory)
		}
	}

	err = unzipDirectory(payload, payloadDir, true)
	if err != nil {
		println("Could not Unzip", err.Error())
		os.Exit(RcCouldNotUnzip)
	}
	os.Remove(TempFile) // do not care about return value
	println("Done")
}

/** Retrieves an URL. Unfortunately we must completely download it, as unzipping needs random access. */
func retrieveUrl(url string) (string, error) {

	if strings.HasPrefix(url, FilePrefix) {
		without := strings.TrimPrefix(url, FilePrefix)
		return without, nil
	} else {
		response, err := http.Get(url)
		if err != nil {
			return "", err
		}
		defer response.Body.Close()
		if response.StatusCode != 200 {
			return "", errors.Errorf("Unexpected status code %d", response.StatusCode)
		}
		output, err := os.OpenFile(TempFile, os.O_WRONLY|os.O_CREATE, 0600)
		if err != nil {
			return "", err
		}
		_, err = io.Copy(output, response.Body)
		defer output.Close()
		if err != nil {
			return "", err
		}
		return TempFile, nil
	}
}

// TODO: Copied from command line util.
func unzipDirectory(zipFile string, directory string, debug bool) error {
	reader, err := zip.OpenReader(zipFile)
	if err != nil {
		return errors.Wrap(err, "Could not open ZIP File")
	}
	defer reader.Close()
	for _, file := range reader.File {
		filePath := filepath.Join(directory, file.Name)
		// Source: https://golangcode.com/unzip-files-in-go/
		// And https://snyk.io/research/zip-slip-vulnerability#go
		if !strings.HasPrefix(filePath, filepath.Clean(directory)+string(os.PathSeparator)) {
			return errors.Errorf("%s illegal file path", filePath)
		}
		if file.FileInfo().IsDir() {
			if debug {
				println("Creating Directory", filePath)
			}
			os.MkdirAll(filePath, os.ModePerm)
		} else {
			if debug {
				println("Unpacking", filePath)
			}
			err := os.MkdirAll(filepath.Dir(filePath), os.ModePerm)
			if err != nil {
				return errors.Errorf("Could not ensure directory of %s", filePath)
			}
			outFile, err := os.OpenFile(filePath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, file.Mode())
			if err != nil {
				return errors.Errorf("Could not create file %s", filePath)
			}

			rc, err := file.Open()
			if err != nil {
				return err
			}
			defer rc.Close()

			_, err = io.Copy(outFile, rc)
			if err != nil {
				return errors.Wrap(err, "Could not write file")
			}
		}
	}
	return nil
}
