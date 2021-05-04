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
	"github.com/pkg/errors"
	"log"
	"sync"
	"time"
)

// A Primitive for waiting for a condition to become true or go into error state.
// Golang Conditions are a bit tricky to handle
type WaitCon struct {
	cond     *sync.Cond
	finished bool
	failed   error
}

func NewWaitCon() *WaitCon {
	mux := sync.Mutex{}
	return &WaitCon{
		cond: sync.NewCond(&mux),
	}
}

/** Wait until the WaitCon finishes (failed or not). */
func (w *WaitCon) Wait() error {
	w.cond.L.Lock()
	for !w.finished {
		w.cond.Wait()
	}
	w.cond.L.Unlock()
	return w.failed
}

var TimeoutError = errors.New("timeout")

func (w *WaitCon) WaitFor(duration time.Duration) error {
	w.cond.L.Lock()
	timedOut := false
	if !w.finished {
		time.AfterFunc(duration, func() {
			w.cond.L.Lock()
			timedOut = true
			w.cond.L.Unlock()
			w.cond.Broadcast()
		})
	}
	for !timedOut && !w.finished {
		w.cond.Wait()
	}
	if timedOut {
		return TimeoutError
	}
	w.cond.L.Unlock()
	return w.failed
}

func (w *WaitCon) Finish(err error) {
	w.cond.L.Lock()
	defer w.cond.L.Unlock()
	if w.finished {
		log.Printf("Wait Condition already done, old result=%s, new result=%s",
			nullableErroToString(w.failed),
			nullableErroToString(err),
		)
	}
	w.finished = true
	w.failed = err
	w.cond.Broadcast()
}

func nullableErroToString(err error) string {
	if err == nil {
		return "nil"
	} else {
		return err.Error()
	}
}
