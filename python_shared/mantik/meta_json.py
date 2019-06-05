# Code for hanndling MetaJson/MetaYaml
import copy
import json
import yaml
from .bundle import Bundle

META_PREFIX = "${"
META_SUFFIX = "}"


class MetaVariable:
    """
    A Meta variable as in the metaVariables block inside Meta Json.
    """

    bundle: Bundle
    """
    MetaVariable value
    """
    name: str
    """
    Name of the MetaVariable
    """
    fix: bool
    """
    If true, the value may not be changed anymore (usually not relevant for Bridges)
    """

    def __init__(self, parsed_json):
        self.bundle = Bundle.decode_parsed_json_bundle(parsed_json)
        self.name = str(parsed_json.get("name"))
        self.fix = bool(parsed_json.get("fix", False))


class MetaVariables:
    """
    Contains multiple meta variables.
    """

    def __init__(self, variables):
        self.variables = variables

    @staticmethod
    def from_parsed(o):
        block = o.get("metaVariables", [])
        variables = []
        for m in block:
            variables.append(MetaVariable(m))
        return MetaVariables(variables)

    def get_value(self, name: str, default_value = None):
        """
        Returns the value of a meta variable.
        Throws if the meta variable is not found and no default is given
        :param name:
        :return:
        """
        for m in self.variables:
            if m.name == name:
                return m.bundle.value
        if default_value == None:
            raise Exception("No meta variable called {} found and no default given".format(name))
        else:
            return default_value


def parse_and_decode_meta_json(meta_json):
    """
    Parse and Decode Meta JSON
    :param meta_json: JSON code
    """
    parsed = json.loads(meta_json)
    return decode_meta_json(parsed)


def parse_and_decode_meta_yaml(meta_yaml):
    """
    Parse and Decode Meta YAML
    :param meta_yaml:
    :return:
    """
    parsed = yaml.load(meta_yaml, Loader=yaml.Loader)
    return decode_meta_json(parsed)


def decode_meta_json(parsed_json):
    """
    Decode metaVariables from the loaded JSON block
    and fills interpolated places.
    :param parsed_json plain python dict, containing the parsed json.
    :return:
    """
    if not isinstance(parsed_json, dict):
        # No metaVariables block possible.
        return copy.deepcopy(parsed_json)

    meta_variables = MetaVariables.from_parsed(parsed_json)

    result = dict()

    for k, v in parsed_json.items():
        if k != "metaVariables":
            result[k] = _decode_meta_json_value(v, meta_variables)
        else:
            result[k] = copy.deepcopy(v)
    return result


def _decode_meta_json_value(v, meta_variables: MetaVariables):
    if isinstance(v, dict):
        return _decode_meta_json_dict(v, meta_variables)
    if isinstance(v, list):
        return _decode_meta_json_list(v, meta_variables)
    if isinstance(v, str):
        return _decode_meta_json_string(v, meta_variables)
    return copy.copy(v)


def _decode_meta_json_dict(o: dict, meta_variables: MetaVariables):
    result = dict()
    for k, v in o.items():
        result[k] = _decode_meta_json_value(v, meta_variables)
    return result


def _decode_meta_json_list(o: list, meta_variables: MetaVariables):
    result = list()
    for v in o:
        result.append(_decode_meta_json_value(v, meta_variables))
    return result


def _decode_meta_json_string(o: str, meta_variables: MetaVariables):
    if o.startswith("$$"):
        # Escaped
        return o[1:]
    if o.startswith(META_PREFIX) and o.endswith(META_SUFFIX):
        variable_name = (o[len(META_PREFIX):])[:-len(META_SUFFIX)]
        value = meta_variables.get_value(variable_name)
        return copy.copy(value)
    else:
        return copy.copy(o)
