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
package async

import (
	"errors"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
	"time"
)

func TestWaitConSuc(t *testing.T) {
	wc := NewWaitCon()

	var wg sync.WaitGroup
	wg.Add(2)
	var r error
	go func() {
		defer wg.Done()
		r = wc.Wait()
	}()
	go func() {
		defer wg.Done()
		time.Sleep(50 * time.Millisecond)
		wc.Finish(nil)
	}()
	wg.Wait()
	assert.NoError(t, r)
	assert.NoError(t, wc.Wait()) // idempotent once it is in a state
}

func TestWaitConErr(t *testing.T) {
	wc := NewWaitCon()

	err := errors.New("Boom")

	var wg sync.WaitGroup
	wg.Add(2)
	var r error
	go func() {
		defer wg.Done()
		r = wc.Wait()
	}()
	go func() {
		defer wg.Done()
		time.Sleep(50 * time.Millisecond)
		wc.Finish(err)
	}()
	wg.Wait()
	assert.Equal(t, err, r)
	assert.Equal(t, err, wc.Wait()) // idempotent once it is in a state
}

func TestWaitCon_TimedWait(t *testing.T) {
	wc := NewWaitCon()
	result := wc.WaitFor(100 * time.Millisecond)
	assert.Equal(t, TimeoutError, result)
}

func TestWaitCon_TimedWaitOk(t *testing.T) {
	wc := NewWaitCon()
	time.AfterFunc(50*time.Millisecond, func() {
		wc.Finish(nil)
	})
	result := wc.WaitFor(100 * time.Millisecond)
	assert.NoError(t, result)
}

func TestWaitCon_AlradyOk(t *testing.T) {
	wc := NewWaitCon()
	err := errors.New("Boom")
	wc.Finish(err)
	result := wc.WaitFor(1 * time.Second)
	assert.Equal(t, err, result)
}
