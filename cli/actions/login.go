package actions

import (
	"cli/client"
	"cli/protos/mantik/engine"
	"context"
	"time"
)

// Arguments for Login command
type LoginArguments struct {
	// url (may be empty to use default)
	Url      string
	Username string
	Password string
}

// Arguments for Logout command
type LogoutArguments struct {
	// empty
}

func Login(client *client.EngineClient, args *LoginArguments) error {
	response, err := client.RemoteRegistry.Login(context.Background(), &engine.LoginRequest{
		Credentials: &engine.LoginCredentials{
			Url:      args.Url,
			Username: args.Username,
			Password: args.Password,
		},
	})
	if err != nil {
		return err
	}

	var validUntil *time.Time
	if response.ValidUntil != nil {
		x := time.Unix(
			response.ValidUntil.Seconds,
			int64(response.ValidUntil.Nanos),
		)
		validUntil = &x
	}

	state := LoginState{
		Url:        response.Token.Url,
		Token:      response.Token.Token,
		ValidUntil: validUntil,
	}

	return storeLoginState(&state)
}

func Logout(engineClient *client.EngineClient, args *LogoutArguments) error {
	// there is not much to do as deleting the file
	return removeLoginState()
}
