package construct

import (
	"bytes"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"io"
	"testing"
)

func TestBinIo(t *testing.T) {
	for _, sample := range element.FundamentalTypeSamples() {
		if sample.Type != ds.Void && sample.Type != ds.String && sample.Type != ds.Bool {
			encoder, err := lookupBinaryWriter(sample.Type.(*ds.FundamentalType))
			assert.NoError(t, err)
			buf := &bytes.Buffer{}
			err = encoder(sample.GetSinglePrimitive(), buf)
			assert.NoError(t, err)
			err = encoder(sample.GetSinglePrimitive(), buf)
			assert.NoError(t, err)

			// and now back
			decoder, err := lookupBinaryReader(sample.Type.(*ds.FundamentalType))
			assert.NoError(t, err)
			reader := bytes.NewReader(buf.Bytes())
			value1, err := decoder(reader)
			assert.NoError(t, err)
			assert.Equal(t, sample.GetSinglePrimitive(), value1)
			value2, err := decoder(reader)
			assert.NoError(t, err)
			assert.Equal(t, sample.GetSinglePrimitive(), value2)
			value3, err := decoder(reader)
			assert.Equal(t, io.EOF, err)
			assert.Nil(t, value3)
		}
	}
}
