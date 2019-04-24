package main

import (
	"fmt"
	"os"
	"text/template"
)

var dsTypeMapping = map[string]string{
	"Uint8":   "uint8",
	"Int8":    "int8",
	"Uint32":  "uint32",
	"Int32":   "int32",
	"Uint64":  "uint64",
	"Int64":   "int64",
	"String":  "string",
	"Bool":    "bool",
	"Float32": "float32",
	"Float64": "float64",
}

type dsTypeConverter struct {
	From string
	To   []string
}

type dsTypeConverterWithMapping struct {
	Converters  []dsTypeConverter
	TypeMapping map[string]string
}

// Available converters
// Design Rule: no loss of information

var converters = []dsTypeConverter{
	{"Uint8", []string{"Int32", "Uint32", "Int64", "Uint64", "Float32", "Float64"}},
	{"Int8", []string{"Int32", "Int64", "Float32", "Float64"}},
	{"Uint32", []string{"Uint64", "Int64", "Float64"}},
	{"Int32", []string{"Int64", "Float64"}},
	{"Float32", []string{"Float64"}},
}

func main() {
	lookupTemplate := template.Must(template.New("lookupTemplate").Parse(lookupFunction))
	fmt.Print(header)

	err := lookupTemplate.Execute(os.Stdout, dsTypeConverterWithMapping{converters, dsTypeMapping})
	if err != nil {
		fmt.Fprintf(os.Stderr, "Something failed %s", err.Error())
		os.Exit(1)
	}
}

var header = `package adapt

// Note: this code is generated by gen/raw_converters.go
// Any changes will be overwritten

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
)

`

var lookupFunction = `
func LookupRawAdapter(from *ds.FundamentalType, to *ds.FundamentalType) (RawAdapter, error) {
	if from == to {
		return emptyRawAdapter, nil
	}
	var result RawAdapter
	switch from {
	{{ range $from, $value := $.Converters }}
	case ds.{{ $value.From }}:
		switch to {
		{{ range $to, $toValue := $value.To }}
			case ds.{{ $toValue }}:
				result = func(i interface{}) interface{} {
					return {{ index $.TypeMapping $toValue }}(i.({{ index $.TypeMapping $value.From }}))
				}
		{{ end }}
		default:
			return nil, errors.Errorf("No raw converter from %s to %s", from.TypeName(), to.TypeName())
		}
	{{ end }}
	default:
		return nil, errors.Errorf("No raw converter from %s", from.TypeName())
	}
	return result, nil
}
`