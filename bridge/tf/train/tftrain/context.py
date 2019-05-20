from abc import abstractmethod
from mantik import Mantikfile

class Context:
    """
    The context which is given to bridge applications
    """

    mantikfile: Mantikfile
    """
    Mantikfile instance
    """


    @abstractmethod
    def meta_variable(self, name: str, default):
        """
        Returns the value of a meta variable or default if not given.
        """
        # No support for meta variables yet
        return default

