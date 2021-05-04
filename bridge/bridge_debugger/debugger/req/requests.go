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
package req

import (
	"encoding/json"
	"fmt"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"time"
)

func GetDataType(baseUrl string, resourceName string) (ds.DataType, error) {
	json, err := GetJson(baseUrl, resourceName)
	if err != nil {
		return nil, err
	}
	return ds.FromJson(json)
}

func GetAlgorithmType(baseUrl string, resourceName string) (*serving.AlgorithmType, error) {
	data, err := GetJson(baseUrl, resourceName)
	if err != nil {
		return nil, err
	}
	var result serving.AlgorithmType
	err = json.Unmarshal(data, &result)
	return &result, err
}

func GetJson(baseUrl string, resourceName string) ([]byte, error) {
	content, err := getResourceContent(baseUrl, resourceName, server.MimeJson)
	if err != nil {
		return nil, err
	}
	defer content.Close()
	bytes, err := ioutil.ReadAll(content)
	if err != nil {
		return nil, err
	}
	return bytes, nil
}

func PostMantikBundle(baseUrl string, resourceName string, reader io.Reader) (io.ReadCloser, error) {
	response, err := PostResource(baseUrl, resourceName, server.MimeMantikBundle, reader)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != 200 {
		return nil, errors.Errorf("Got %d response", response.StatusCode)
	}
	return response.Body, nil
}

// Request a mantik bundle
func GetMantikBundle(baseUrl string, resourceName string) (io.ReadCloser, error) {
	return getResourceContent(baseUrl, resourceName, server.MimeMantikBundle)
}

// Requesting ZIP
func GetZipFile(baseUrl string, resourceName string) (io.ReadCloser, error) {
	return getResourceContent(baseUrl, resourceName, server.MimeZip)
}

func getResourceContent(baseUrl string, resourceName string, accept string) (io.ReadCloser, error) {
	response, err := getResource(baseUrl, resourceName, &accept)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != 200 {
		return nil, errors.Errorf("Got %d response", response.StatusCode)
	}
	return response.Body, nil
}

func getResource(baseUrl string, resourceName string, accept *string) (*http.Response, error) {
	resource, err := ResolveResource(baseUrl, resourceName)
	if err != nil {
		return nil, err
	}
	for {
		request, err := http.NewRequest(http.MethodGet, resource, nil)
		if err != nil {
			return nil, err
		}
		if accept != nil {
			request.Header.Add("Accept", *accept)
		}
		response, err := http.DefaultClient.Do(request)
		if err != nil {
			return nil, err
		}
		if response.StatusCode == 409 {
			fmt.Printf("Received 409 on waiting for %s resource, waiting 1s\n", resource)
			time.Sleep(time.Second * 1)
			continue
		}
		return response, err
	}
}

func PostResource(baseUrl string, resourceName string, contentType string, reader io.Reader) (*http.Response, error) {
	resource, err := ResolveResource(baseUrl, resourceName)
	if err != nil {
		return nil, err
	}
	request, err := http.NewRequest(http.MethodPost, resource, reader)
	if err != nil {
		return nil, err
	}
	request.Header.Add("Content-Type", contentType)
	return http.DefaultClient.Do(request)
}

func ResolveResource(baseUrl string, resourceName string) (string, error) {
	base, err := url.Parse(baseUrl)
	if err != nil {
		return "", errors.Wrap(err, "Invalid URL")
	}
	res, err := url.Parse(resourceName)
	if err != nil {
		panic(err.Error())
	}
	resolved := base.ResolveReference(res)
	return resolved.String(), nil
}
