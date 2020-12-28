package selectbridge

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"sort"
	"testing"
)

var joinTest1Input1 = builder.RowsAsElements(
	builder.PrimitiveRow(int32(1), int32(2)),
	builder.PrimitiveRow(int32(2), int32(3)),
)

var joinTest1Input2 = builder.RowsAsElements(
	builder.PrimitiveRow(int32(1), "Hello"),
	builder.PrimitiveRow(int32(3), "World"),
)

func sortByElement(elements []element.Element, idx int, dt ds.DataType) {
	comparator, err := natural.LookupComparator(dt)
	if err != nil {
		panic(err.Error())
	}
	sort.Slice(elements, func(i, j int) bool {
		left := elements[i].(*element.TabularRow).Columns[idx]
		right := elements[j].(*element.TabularRow).Columns[idx]
		return comparator.Compare(left, right) < 0
	})
}

func sortTable(elements []element.Element, dt ds.DataType) {
	comparator, err := natural.LookupComparator(dt)
	if err != nil {
		panic(err.Error())
	}
	sort.Slice(elements, func(i, j int) bool {
		left := elements[i].(*element.TabularRow)
		right := elements[j].(*element.TabularRow)
		return comparator.Compare(left, right) < 0
	})
}

// Note: this examples can be created by using a Scal Worksheet like this
/*
import ai.mantik.ds.sql.{Query, Select, SqlContext}
import ai.mantik.ds.{FundamentalType, Nullable, TabularData}
import ai.mantik.planner.select.SelectMantikHeaderBuilder

val leftInput = TabularData(
  "x" -> FundamentalType.Int32,
  "y" -> FundamentalType.Int32
)

val rightInput = TabularData(
  "x" -> FundamentalType.Int32,
  "z" -> FundamentalType.StringType
)

implicit val sqlContext = SqlContext(
  Vector(
    leftInput,rightInput
  )
)

// Update query here
val query = Query.parse("SELECT l.x, r.x FROM $0 AS l JOIN $1 AS r ON l.x + r.x = 2").right

println(s"Query = ${query}")

val header = SelectMantikHeaderBuilder.compileToMantikHeader(query.get).right.get
print(header.toYaml)
*/

func TestJoin1InnerUsing(t *testing.T) {
	model, err := LoadModel("../../examples/join1_inner_using")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), int32(2), "Hello"),
	)

	transformed, err := model.Execute(joinTest1Input1, joinTest1Input2)
	sortByElement(transformed, 0, ds.Int32)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestJoin2LeftUsing(t *testing.T) {
	model, err := LoadModel("../../examples/join2_left_using")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), int32(2), "Hello"),
		builder.PrimitiveRow(int32(2), int32(3), nil),
	)

	transformed, err := model.Execute(joinTest1Input1, joinTest1Input2)
	sortByElement(transformed, 0, ds.Int32)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestJoin3RightUsing(t *testing.T) {
	model, err := LoadModel("../../examples/join3_right_using")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(2), int32(1), "Hello"),
		builder.PrimitiveRow(nil, int32(3), "World"),
	)

	transformed, err := model.Execute(joinTest1Input1, joinTest1Input2)
	sortByElement(transformed, 1, ds.Int32)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestJoin4OuterUsing(t *testing.T) {
	model, err := LoadModel("../../examples/join4_outer_using")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), int32(2), "Hello"),
		builder.PrimitiveRow(int32(2), int32(3), nil),
		builder.PrimitiveRow(int32(3), nil, "World"),
	)

	transformed, err := model.Execute(joinTest1Input1, joinTest1Input2)
	sortByElement(transformed, 0, &ds.Nullable{ds.TypeReference{ds.Int32}})

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestJoin5OuterOn(t *testing.T) {
	model, err := LoadModel("../../examples/join5_outer_on")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(nil, "World"),
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), nil),
	)

	transformed, err := model.Execute(joinTest1Input1, joinTest1Input2)
	sortByElement(transformed, 0, &ds.Nullable{ds.TypeReference{ds.Int32}})

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestJoin6Cross(t *testing.T) {
	model, err := LoadModel("../../examples/join6_cross_join")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), int32(1)),
		builder.PrimitiveRow(int32(1), int32(3)),
		builder.PrimitiveRow(int32(2), int32(1)),
		builder.PrimitiveRow(int32(2), int32(3)),
	)

	transformed, err := model.Execute(joinTest1Input1, joinTest1Input2)
	sortTable(transformed, model.Outputs()[0].Underlying)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestJoin7InnerOn(t *testing.T) {
	model, err := LoadModel("../../examples/join7_inner_on")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), int32(1)),
	)

	transformed, err := model.Execute(joinTest1Input1, joinTest1Input2)
	sortTable(transformed, model.Outputs()[0].Underlying)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}
