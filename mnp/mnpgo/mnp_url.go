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
package mnpgo

import (
	"fmt"
	"github.com/pkg/errors"
	"net/url"
	"strconv"
	"strings"
)

type MnpUrl struct {
	// Hostname:HostPort
	Address string
	// MNP SessionId
	SessionId string
	// MNP Port (-1 if there is no port)
	Port int
}

func (u *MnpUrl) String() string {
	if u.Port == -1 {
		return fmt.Sprintf("mnp://%s/%s", u.Address, u.SessionId)
	} else {
		return fmt.Sprintf("mnp://%s/%s/%d", u.Address, u.SessionId, u.Port)
	}
}

func ParseMnpUrl(mnpUrl string) (*MnpUrl, error) {
	parsed, err := url.Parse(mnpUrl)
	if err != nil {
		return nil, err
	}
	if parsed.Scheme != "mnp" {
		return nil, errors.Errorf("Unexpected scheme %s, wanted mnp", parsed.Scheme)
	}
	var result MnpUrl
	result.Address = parsed.Host
	result.Port = -1 // no port
	parts := strings.Split(parsed.Path, "/")
	n := len(parts)
	// path starts with /
	if n < 2 {
		return nil, errors.New("Bad path")
	}
	result.SessionId = parts[1]
	if n == 3 {
		port, err := strconv.Atoi(parts[2])
		if err != nil {
			return nil, errors.Wrap(err, "Bad port")
		}
		result.Port = port
	}
	return &result, nil
}
