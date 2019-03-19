package rpc_utils

import (
	"context"
	"coordinator/testutil"
	"fmt"
	"github.com/stretchr/testify/assert"
	"net"
	"net/rpc"
	"testing"
	"time"
)

func TestCallRpcWithTimeout(t *testing.T) {
	port := testutil.GetFreeTcpListeningPort()
	addr := fmt.Sprintf("localhost:%d", port)
	listener, err := net.Listen("tcp", addr)
	defer listener.Close()
	assert.NoError(t, err)
	client, err := ConnectRpcWithTimeout(context.Background(), addr, time.Second)
	assert.NoError(t, err)
	var reply *int
	err = CallRpcWithTimeout(context.Background(), time.Millisecond*100, client, "foo", 1, &reply)
	assert.Equal(t, RpcTimeout, err)

	ctx2, canceller := context.WithCancel(context.Background())
	time.AfterFunc(10*time.Millisecond, func() {
		canceller()
	})
	err = CallRpcWithTimeout(ctx2, time.Second, client, "foo", 1, &reply)
	assert.Equal(t, context.Canceled, err)
}

func TestConnectRpcWithTimeoutMultipleGivup(t *testing.T) {
	port := testutil.GetFreeTcpListeningPort()
	t0 := time.Now()
	c, err := ConnectRpcWithTimeoutMultiple(context.Background(), fmt.Sprintf("localhost:%d", port), time.Millisecond*100, time.Millisecond*10)
	t1 := time.Now()
	assert.Nil(t, c)
	assert.Error(t, err)
	dif := t1.Sub(t0)
	assert.True(t, 200*time.Millisecond > dif)
	assert.True(t, 80*time.Millisecond < dif)
}

func TestConnectWithCanceledContext(t *testing.T) {
	ctx, f := context.WithCancel(context.Background())
	f()

	port := testutil.GetFreeTcpListeningPort()
	t0 := time.Now()
	c, err := ConnectRpcWithTimeoutMultiple(ctx, fmt.Sprintf("localhost:%d", port), time.Millisecond*100, time.Millisecond*10)
	t1 := time.Now()
	assert.Nil(t, c)
	assert.Equal(t, err, context.Canceled)
	assert.WithinDuration(t, t1, t0, 10*time.Millisecond)
}

func TestConnectRpcWithTimeoutSuccess(t *testing.T) {
	port := testutil.GetFreeTcpListeningPort()
	address := fmt.Sprintf("localhost:%d", port)
	listener, err := net.Listen("tcp", address)
	assert.NoError(t, err)
	defer listener.Close()
	go func() {
		rpc.Accept(listener)
	}()
	t0 := time.Now()
	c, err := ConnectRpcWithTimeoutMultiple(context.Background(), address, time.Millisecond*100, time.Millisecond*10)
	t1 := time.Now()
	assert.WithinDuration(t, t0, t1, time.Millisecond*20)
	assert.NoError(t, err)
	assert.NotNil(t, c)
	c.Close()

}
