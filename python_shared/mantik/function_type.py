from .data_type import DataType
import json


# Function Mapping
class FunctionType(object):

    def __init__(self, input_type: DataType, output_type: DataType):
        self.input = input_type
        self.output = output_type

    def __repr__(self):
        return "{} -> {}".format(self.input, self.output)

    @staticmethod
    def from_dict(o):
        """
        Generates a function from the result of JSON Parsing.
        """
        return FunctionType(DataType(o.get("input")), DataType(o.get("output")))

    def to_json(self):
        return json.dumps({
            "input": self.input.representation,
            "output": self.output.representation
        })
