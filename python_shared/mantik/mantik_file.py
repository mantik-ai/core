import yaml
import os
from .function_type import FunctionType
from .data_type import DataType
from .meta_json import MetaVariables
from .meta_json import parse_and_decode_meta_yaml

class Mantikfile(object):
    """
    Represents the Mantikfile which controls the way a algorithm/dataset works.
    """

    meta_variables: MetaVariables

    def __init__(self, parsed_yaml_code, basedir):
        self.yaml_code = parsed_yaml_code
        self.directory = parsed_yaml_code.get("directory", None)
        self.basedir = basedir
        self.name = parsed_yaml_code.get("name", "unnamed")
        self.type = FunctionType.from_dict(parsed_yaml_code.get("type"))  # Type as Python Object
        self.meta_variables = MetaVariables.from_parsed(parsed_yaml_code)
        self.kind = parsed_yaml_code.get("kind", "algorithm")
        tt = parsed_yaml_code.get("trainingType", None)
        if tt is None:
            self.training_type = None
        else:
            self.training_type = DataType(tt)
        st = parsed_yaml_code.get("statType", None)
        if st is None:
            self.stat_type = None
        else:
            self.stat_type = DataType(st)

    def has_training(self):
        return self.training_type is not None

    @staticmethod
    def load(file: str):
        """
        Load a local mantik file
        """
        with(open(file)) as f:
            return Mantikfile.parse(f, os.path.dirname(file))

    @staticmethod
    def parse(content: str, root_dir):
        y = parse_and_decode_meta_yaml(content)
        return Mantikfile(y, root_dir)

    def payload_dir(self):
        """
        Returns the payload directory
        """
        return os.path.join(self.basedir, self.directory)
