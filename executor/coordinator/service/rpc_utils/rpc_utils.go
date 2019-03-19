package rpc_utils

import (
	"context"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"net"
	"net/rpc"
	"net/rpc/jsonrpc"
	"time"
)

var RpcTimeout = errors.New("Timeout on RPC Call")

func CallRpcWithTimeout(ctx context.Context, timeout time.Duration, client *rpc.Client, serviceMethod string, args interface{}, reply interface{}) error {
	if ctx.Err() != nil {
		return ctx.Err()
	}
	done := make(chan error, 1)
	go func() {
		done <- client.Call(serviceMethod, args, reply)
	}()

	select {
	case err := <-done:
		return err
	case <-time.After(timeout):
		return RpcTimeout
	case <-ctx.Done():
		return ctx.Err()
	}
}

// Connect RPC with Timeout
func ConnectRpcWithTimeout(ctx context.Context, address string, timeout time.Duration) (*rpc.Client, error) {
	d := net.Dialer{Timeout: timeout}
	con, err := d.DialContext(ctx, "tcp", address)
	if err != nil {
		return nil, err
	}
	client := jsonrpc.NewClient(con)
	return client, nil
}

/** Connect to some address and tries again until it works (waiting for waitTime) or if the final timeout breaks. */
func ConnectRpcWithTimeoutMultiple(ctx context.Context, address string, timeout time.Duration, waitTime time.Duration) (*rpc.Client, error) {
	finalTimeout := time.Now().Add(timeout)
	var lastErr error
	trials := 0
	for {
		current := time.Now()
		if current.After(finalTimeout) && lastErr != nil {
			logrus.Infof("Could not connect to address %s, within %s, trials %d", address, timeout.String(), trials)
			return nil, errors.Wrapf(lastErr, "Could not connect to %s within %s", address, timeout.String())
		}
		pending := finalTimeout.Sub(current)
		var c *rpc.Client
		c, lastErr = ConnectRpcWithTimeout(ctx, address, pending)
		trials += 1
		if lastErr == nil {
			logrus.Debugf("Connect to %s using %d trials", address, trials)
			return c, nil
		}
		select {
		case <-time.After(waitTime):
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
}
