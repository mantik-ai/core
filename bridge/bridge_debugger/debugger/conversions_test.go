package debugger

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestParseSelectColumns(t *testing.T) {
	_, err := ParseSelectColumns("")
	assert.Error(t, err)

	values, err := ParseSelectColumns("foo")
	assert.NoError(t, err)
	assert.Equal(t, ColumnSelector{ColumnPair{"foo", "foo"}}, values)

	values, err = ParseSelectColumns("foo:bar")
	assert.NoError(t, err)
	assert.Equal(t, ColumnSelector{ColumnPair{"foo", "bar"}}, values)

	values, err = ParseSelectColumns("foo:bar,buz")
	assert.NoError(t, err)
	assert.Equal(t, ColumnSelector{
		ColumnPair{"foo", "bar"},
		ColumnPair{"buz", "buz"},
	}, values)
}
