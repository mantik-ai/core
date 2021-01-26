package debugger

import (
	"fmt"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"os"
	"strings"
)

func MantikBundleToJson(inputFile string, outputFile string) {
	bundleConvert(serializer.BACKEND_MSGPACK, serializer.BACKEND_JSON, inputFile, outputFile)
}

func JsonBundleToMantik(inputFile string, outputFile string) {
	bundleConvert(serializer.BACKEND_JSON, serializer.BACKEND_MSGPACK, inputFile, outputFile)
}

type ColumnPair struct {
	From string
	To   string
}
type ColumnSelector []ColumnPair

func ParseSelectColumns(columns string) (ColumnSelector, error) {
	values := strings.Split(columns, ",")
	result := make(ColumnSelector, 0, 0)
	for _, value := range values {
		if len(value) == 0 {
			continue
		}
		parts := strings.Split(value, ":")
		if len(parts) > 2 {
			return nil, errors.New("Invalid selector")
		}
		if len(parts) == 2 {
			result = append(result, ColumnPair{parts[0], parts[1]})
		} else {
			result = append(result, ColumnPair{value, value})
		}
	}
	if len(result) == 0 {
		return nil, errors.New("Empty input")
	}
	return result, nil
}

func SelectColumns(inputFile string, outputFile string, columns ColumnSelector) {
	// TODO: This is also not very efficient, as selecting could be done on the fly.
	inputBundle, err := readBundleFromFile(inputFile)
	ExitOnError(err, "Loading Input File")
	cast, err := buildSelectAdapter(inputBundle.Type, columns)
	ExitOnError(err, "Building Adapter")
	resultRows, err := adapt.ApplyForMany(cast.Adapter, inputBundle.Rows)
	ExitOnError(err, "Adapting")
	resultBundle := element.Bundle{cast.To, resultRows}
	err = writeBundleToFile(&resultBundle, outputFile)
	ExitOnError(err, "Writing Result")
}

func buildSelectAdapter(dataType ds.DataType, selector ColumnSelector) (*adapt.Cast, error) {
	tab, isTabular := dataType.(*ds.TabularData)
	if !isTabular {
		return nil, errors.New("Only tabular types supported")
	}
	ids := make([]int, 0, 0)
	var resultType ds.TabularData
	for _, pair := range selector {
		fromId := tab.IndexOfColumn(pair.From)
		if fromId < 0 {
			return nil, errors.Errorf("Column %s not found", pair.From)
		}
		resultType.Columns = append(resultType.Columns, ds.NamedType{pair.To, tab.Columns[fromId].SubType})
		ids = append(ids, fromId)
	}
	var adapter adapt.Adapter = func(e element.Element) (element.Element, error) {
		input := e.(*element.TabularRow)
		output := make([]element.Element, len(ids), len(ids))
		for dstId, fromId := range ids {
			output[dstId] = input.Columns[fromId]
		}
		return &element.TabularRow{output}, nil
	}
	return &adapt.Cast{
		From:    dataType,
		To:      &resultType,
		Loosing: false,
		CanFail: false,
		Adapter: adapter,
	}, nil
}

func PrintBundle(inputFile string, rows int) {
	inputBundle, err := readBundleFromFile(inputFile)
	ExitOnError(err, "Loading Input File")
	selectedRows := inputBundle.Rows[:rows]
	println("Data Type: ", ds.ToJsonString(inputBundle.Type))
	rootElementSerializer, err := natural.LookupRootElementSerializer(inputBundle.Type)
	ExitOnError(err, "Could not create serializer")
	backend, err := serializer.CreateSerializingBackend(serializer.BACKEND_JSON, os.Stdout)
	ExitOnError(err, "Could not create backend")

	for _, r := range selectedRows {
		rootElementSerializer.Write(backend, r)
		fmt.Fprint(os.Stdout, "\n")
	}
}

func bundleConvert(load serializer.BackendType, save serializer.BackendType, inputFile string, outputFile string) {
	f, err := os.Open(inputFile)
	ExitOnError(err, "Open Input File")
	defer f.Close()
	bundle, err := natural.DecodeBundleFromReader(load, f)
	ExitOnError(err, "Decoding Input")

	o, err := os.OpenFile(outputFile, os.O_WRONLY|os.O_CREATE, 0660)
	ExitOnError(err, "Open Output file")
	defer o.Close()

	err = natural.EncodeBundleToWriter(bundle, save, o)
	ExitOnError(err, "Writing Bundle")
}
