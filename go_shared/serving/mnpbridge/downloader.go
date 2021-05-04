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
package mnpbridge

import (
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"io"
	"net"
	"net/http"
	"os"
	"strings"
	"time"
)

// TODO Testing!

/* Downloads a payload file, tries to do some clever retrying. */
func DownloadPayload(url string, destination *os.File) error {
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
		err := retrieveUrl(httpClient, url, destination)
		if err == nil {
			logrus.Infof("Downloaded %s in %d trials (%s)\n", url, trials, time.Now().Sub(start).String())
			return nil
		}
		ct := time.Now()
		if ct.After(end) {
			return errors.Wrapf(err, "Timeout by connecting, tried %d within %s", trials, timeout.String())
		}
		time.Sleep(waitStep)
	}
}

const FilePrefix = "file://"

/** Retrieves an URL. Unfortunately we must completely download it, as unzipping needs random access. */
func retrieveUrl(
	httpClient *http.Client, url string,
	destination *os.File,
) error {
	_, err := destination.Seek(0, 0)
	if err != nil {
		return err
	}
	if strings.HasPrefix(url, FilePrefix) {
		// Copy file url directly into the target.
		without := strings.TrimPrefix(url, FilePrefix)
		source, err := os.Open(without)
		defer source.Close()
		if err != nil {
			return err
		}
		_, err = io.Copy(destination, source)
		return err
	} else {
		response, err := httpClient.Get(url)
		if err != nil {
			return err
		}
		defer response.Body.Close()
		if response.StatusCode != 200 {
			return errors.Errorf("Unexpected status code %d", response.StatusCode)
		}
		_, err = io.Copy(destination, response.Body)
		if err != nil {
			return err
		}
		return nil
	}
}
