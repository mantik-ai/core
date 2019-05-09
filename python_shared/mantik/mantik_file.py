import yaml
import os
from .function_type import FunctionType
from .data_type import DataType


# Parses the most important stuff from a Mantikfile
class Mantikfile(object):

    def __init__(self, yaml_code, basedir):
        self.yaml_code = yaml_code
        self.directory = yaml_code.get("directory", None)
        self.basedir = basedir
        self.name = yaml_code.get("name", "unnamed")
        self.type = FunctionType.from_dict(yaml_code.get("type"))  # Type as Python Object
        tt = yaml_code.get("trainingType", None)
        if tt is None:
            self.training_type = None
        else:
            self.training_type = DataType(tt)
        st = yaml_code.get("statType", None)
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
            y = yaml.load(f, Loader=yaml.Loader)
            return Mantikfile(y, os.path.dirname(file))

    def payload_dir(self):
        """
        Returns the payload directory
        """
        return os.path.join(self.basedir, self.directory)
