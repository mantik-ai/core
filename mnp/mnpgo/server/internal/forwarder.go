package internal

import (
	"context"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/util"
	"google.golang.org/grpc"
	"io"
)

/* An object which should forward data to some next entity. */
type Forwarder struct {
	con     *grpc.ClientConn
	service mnp.MnpServiceClient
	url     *mnpgo.MnpUrl
}

/* Connects to a remote node and creates a forwarder. */
func ConnectForwarder(ctx context.Context, forwardingUrl string) (*Forwarder, error) {
	parsed, err := mnpgo.ParseMnpUrl(forwardingUrl)
	if err != nil {
		return nil, err
	}
	clientCon, err := grpc.DialContext(ctx, parsed.Address, grpc.WithInsecure(), grpc.WithBlock())
	if err != nil {
		return nil, err
	}
	return &Forwarder{
		con:     clientCon,
		service: mnp.NewMnpServiceClient(clientCon),
		url:     parsed,
	}, nil
}

func (f *Forwarder) RunTask(ctx context.Context, taskId string) (io.WriteCloser, error) {
	return util.WrapPushToWriter(
		f.service,
		ctx,
		f.url.SessionId,
		taskId,
		f.url.Port,
	)
}

func (f *Forwarder) Close() {
	f.con.Close()
}
