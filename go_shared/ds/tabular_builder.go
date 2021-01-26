package ds

type TabularTypeBuilder struct {
	columns []NamedType
}

func BuildTabular() *TabularTypeBuilder {
	return &TabularTypeBuilder{}
}

func (t *TabularTypeBuilder) Add(name string, dataType DataType) *TabularTypeBuilder {
	t.columns = append(t.columns, NamedType{name, Ref(dataType)})
	return t
}

func (t *TabularTypeBuilder) AddTensor(name string, componentType DataType, shape []int) *TabularTypeBuilder {
	tensorType := Tensor{ComponentType: Ref(componentType), Shape: shape}
	return t.Add(name, &tensorType)
}

func (t *TabularTypeBuilder) Result() *TabularData {
	return &TabularData{
		t.columns,
		nil,
	}
}
