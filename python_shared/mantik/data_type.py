import json


class DataType:

    """
    Mantik ds.DataType.
    Note: this is just a thin wrapper around the parsed JSON/YAML Representation
    """

    # Note: in column declarations the insertion order is important
    # As of Python 3.7 dicts preserve order (see https://docs.python.org/3/whatsnew/3.7.html )
    # Before that it should also be the case, but wasn't part of the standard.

    # Initializes from the parsed JSON representation
    def __init__(self, representation):
        self.representation = representation

    @staticmethod
    def parse_json(json_string) -> 'DataType':
        # Note: column representation needs to be a OrderedDict in practice
        return DataType(json.loads(json_string))

    def is_tabular(self) -> bool:
        """
        Returns true if the type is tabular
        """
        return self.sub_type() == "tabular"

    def sub_type(self) -> bool:
        """
        Returns the sub type of the data type.
        (This is either fundamental or the "type" element of the JSON representation)
        :return:
        """
        if isinstance(self.representation, dict):
            return self.representation.get("type", "tabular")
        if isinstance(self.representation, str):
            return "fundamental"
        raise Exception("Invalid representation type")


    def to_json(self) -> str:
        return json.dumps(self.representation)

    def column_id(self, column_name: str) -> int:
        """
        Returns the index of a column, data type must be tabular.
        :returns the index of the column or raise an exception
        """
        assert self.is_tabular()
        columns = self.representation.get("columns")
        index = 0
        for k, _ in columns.items():
            if k == column_name:
                return index
            index += 1
        raise Exception("Column {} not found".format(column_name))

    def column_names(self) -> list:
        """
        Return then number of columns
        :return:
        """
        assert self.is_tabular()
        columns = self.representation.get("columns")
        return list(columns.keys())

    def column_type(self, name: str) -> 'DataType':
        """
        Return the Column type as data type. Must be tabular
        :param name:
        :return:
        """
        assert self.is_tabular()
        columns = self.representation.get("columns")
        return DataType(columns.get(name))

    def __eq__(self, other):
        if isinstance(other, DataType):
            return self.representation == other.representation
        else:
            return False

    def __repr__(self):
        return str(self.representation)
