from mantik.types import Mantikfile
import json


def test_parse():
    sample = """
metaVariables:
  - name: width
    type: int32
    value: 100
type:
  input:
    columns:
      x:
        type: tensor
        componentType: float32
        shape: ["${width}"]
  output: float32 
    """

    mf = Mantikfile.parse(sample, ".")
    assert mf.payload_dir == "./payload"
    assert mf.type.input.representation == json.loads(
        '{"columns": {"x":{"type":"tensor","componentType":"float32","shape":[100]}}}'
    )
    assert mf.type.output.representation == json.loads('"float32"')
    assert mf.kind == "algorithm"
    assert mf.name == "unnamed"
    assert mf.meta_variables.get("width") == 100
