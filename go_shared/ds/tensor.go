package ds

type Tensor struct {
	ComponentType TypeReference `json:"componentType"`
	Shape         []int         `json:"shape"`
}

func (t *Tensor) IsFundamental() bool {
	return false
}

func (t *Tensor) TypeName() string {
	return "tensor"
}

func (t *Tensor) PackedElementCount() int {
	var p = int(1)
	for _, v := range t.Shape {
		p = p * int(v)
	}
	return p
}
