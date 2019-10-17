package cmd

import (
	"cli/client"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestParseArguments_Global(t *testing.T) {
	args, err := ParseArguments([]string{"app"}, "")
	assert.NoError(t, err)
	assert.Equal(t, client.DefaultPort, args.ClientArgs.Port)
	assert.Equal(t, client.DefaultHost, args.ClientArgs.Host)
}

func TestParseArguments_GlobalOverride(t *testing.T) {
	args, err := ParseArguments([]string{"app", "--host", "foo", "--port", "1234"}, "")
	assert.NoError(t, err)
	assert.Equal(t, 1234, args.ClientArgs.Port)
	assert.Equal(t, "foo", args.ClientArgs.Host)
}

func TestParseArguments_About(t *testing.T) {
	args, err := ParseArguments([]string{"app", "version"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Version)
}

func TestParseArguments_Items(t *testing.T) {
	args, err := ParseArguments([]string{"app", "items", "--deployed", "--kind", "dataset", "--all", "--deployed", "--noTable"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Items)
	assert.Equal(t, true, args.Items.Anonymous)
	assert.Equal(t, true, args.Items.NoTable)
	assert.Equal(t, "dataset", args.Items.Kind)
	assert.Equal(t, true, args.Items.Deployed)
}

func TestParseArguments_Item(t *testing.T) {
	args, err := ParseArguments([]string{"app", "item", "foobar"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Item)
	assert.Equal(t, "foobar", args.Item.MantikId)

	_, err = ParseArguments([]string{"app", "item"}, "")
	assert.Equal(t, MissingArgument, err)
}

func TestParseArguments_Deploy(t *testing.T) {

	args, err := ParseArguments([]string{"app", "deploy", "foobar", "--ingress", "ingress1", "--nameHint", "name1"}, "")
	assert.NoError(t, err)
	assert.NotNil(t, args.Deploy)
	assert.Equal(t, "foobar", args.Deploy.MantikId)
	assert.Equal(t, "ingress1", args.Deploy.IngressName)
	assert.Equal(t, "name1", args.Deploy.NameHint)

	_, err = ParseArguments([]string{"app", "deploy"}, "")
	assert.Equal(t, MissingArgument, err)
}
