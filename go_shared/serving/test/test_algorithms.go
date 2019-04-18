package test

import (
	"encoding/json"
	"fmt"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"io/ioutil"
	"os"
	"path"
)

var threeTimesTypeJson = `
{
	"input": {
		"columns": {
			"x": "int32"
		}
	},
	"output": {
		"columns": {
			"y": "int32"
		}
	}
}
`

type threeTimes struct {
	algorithmType *serving.AlgorithmType
}

func NewThreeTimes() serving.ExecutableAlgorithm {
	return &threeTimes{}
}

func (t *threeTimes) Type() *serving.AlgorithmType {
	if t.algorithmType == nil {
		var x serving.AlgorithmType
		json.Unmarshal([]byte(threeTimesTypeJson), &x)
		t.algorithmType = &x
	}
	return t.algorithmType
}

func (t *threeTimes) NativeType() *serving.AlgorithmType {
	return t.Type()
}

func (t *threeTimes) Execute(rows []element.Element) ([]element.Element, error) {
	var result []element.Element
	for _, v := range rows {
		i := v.(*element.TabularRow).Columns[0].(element.Primitive).X.(int32)
		r := builder.PrimitiveRow(int32(i * 3))
		result = append(result, r)
	}
	return result, nil
}

func (t *threeTimes) Cleanup() {
	// nothing
}

func (t *threeTimes) ExtensionInfo() interface{} {
	panic("implement me")
}

// Training: Return the sum of all Elements
type trainAlgorithm struct {
	learned bool
	sum     int32
	tempDir string
}

func (l *trainAlgorithm) TrainingType() ds.TypeReference {
	return ds.FromJsonStringOrPanicRef(
		`
	{
			"columns": {
				"x": "int32"
			}
	}`)
}

func (l *trainAlgorithm) StatType() ds.TypeReference {
	return ds.FromJsonStringOrPanicRef(`
		{
			"columns": {
				"sum": "int32"
			}
		}
`)
}

func (l *trainAlgorithm) Train(rows []element.Element) ([]element.Element, error) {
	var summer int32 = 0
	for _, v := range rows {
		i := v.(*element.TabularRow).Columns[0].(element.Primitive).X.(int32)
		summer += i
	}
	var result []element.Element
	result = append(result, builder.PrimitiveRow(summer))
	l.learned = true
	l.sum = summer
	return result, nil
}

func NewLearnAlgorithm() *trainAlgorithm {
	dir, err := ioutil.TempDir("", "test")
	if err != nil {
		panic(err.Error())
	}
	return &trainAlgorithm{
		tempDir: dir,
	}
}

func (l *trainAlgorithm) Type() *serving.AlgorithmType {
	t, e := serving.ParseAlgorithmType(threeTimesTypeJson)
	if e != nil {
		panic(e.Error())
	}
	return t
}

func (l *trainAlgorithm) NativeType() *serving.AlgorithmType {
	return l.Type()
}

func (l *trainAlgorithm) Cleanup() {
	os.RemoveAll(l.tempDir)
}

func (l *trainAlgorithm) ExtensionInfo() interface{} {
	panic("implement me")
}

func (l *trainAlgorithm) LearnResultDirectory() (string, error) {
	if !l.learned {
		return "", errors.New("Not learned yet!")
	}
	file := path.Join(l.tempDir, "result")
	output, err := os.OpenFile(file, os.O_WRONLY|os.O_CREATE, 0644)
	defer output.Close()
	if err != nil {
		return "", err
	}
	_, err = fmt.Fprintf(output, "Train result: %d", l.sum)
	if err != nil {
		return "", err
	}
	return l.tempDir, nil
}
