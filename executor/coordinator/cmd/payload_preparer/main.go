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
	"net"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"strings"
	"syscall"
	"time"
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
	url := options.String("url", "", "Optional Url to download")
	dir := options.String("dir", "/data", "Destination Directory")
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
		if len(*mantikfile) == 0 {
			println("Nothing to do, no URL no mantikfile")
			os.Exit(0)
		}
		println("Continuing with empty URL")
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

	if len(*url) == 0 {
		// Stopping now, no data payload
		os.Exit(0)
	}

	payload, err := retrieveUrlWithTimeout(*url)
	if err != nil {
		println("Could not read URL", err.Error())
		os.Exit(RcCouldNotReadUrl)
	}

	payloadDir := path.Join(*dir, "payload")

	// It is import that the created directory is writeable for the same group, as bridges are allowed to write there
	// default umask is 0002, which will make it non-group-writeable.
	oldUmask := syscall.Umask(02)
	err = os.MkdirAll(payloadDir, os.ModePerm) // note: the bridge container may also write into it and is of same group
	syscall.Umask(oldUmask)
	if err != nil {
		println("Could not create payload sub directory")
		os.Exit(RcCouldNotCreateDirectory)
	}

	err = unzipDirectory(payload, payloadDir, true)
	if err != nil {
		println("Could not Unzip", err.Error())
		os.Exit(RcCouldNotUnzip)
	}
	os.Remove(TempFile) // do not care about return value
	println("Done")
}

/** Retrieves an URL and download the content somewhere. */
func retrieveUrlWithTimeout(url string) (string, error) {
	timeout := time.Duration(120 * time.Second)
	getTimeout := time.Duration(60 * time.Second)
	start := time.Now()
	end := start.Add(timeout)
	waitStep := time.Duration(100 * time.Millisecond)

	// See https://blog.cloudflare.com/the-complete-guide-to-golang-net-http-timeouts/
	httpClient := &http.Client{
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout: 5 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   10 * time.Second,
			ResponseHeaderTimeout: 10 * time.Second,
			ExpectContinueTimeout: 1 * time.Second,
		},
		Timeout: getTimeout,
	}

	var trials = 0
	for {
		trials += 1
		result, err := retrieveUrl(httpClient, url)
		if err == nil {
			fmt.Fprintf(os.Stderr, "Downloaded %s in %d trials (%s)\n", url, trials, time.Now().Sub(start).String())
			return result, nil
		}
		ct := time.Now()
		if ct.After(end) {
			return "", errors.Wrapf(err, "Timeout by connecting, last error, tried %d within %s", trials, timeout.String())
		}
		time.Sleep(waitStep)
	}
}

/** Retrieves an URL. Unfortunately we must completely download it, as unzipping needs random access. */
func retrieveUrl(
	httpClient *http.Client, url string,
) (string, error) {

	if strings.HasPrefix(url, FilePrefix) {
		without := strings.TrimPrefix(url, FilePrefix)
		return without, nil
	} else {
		response, err := httpClient.Get(url)
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
