import abc
from mantik.types import Bundle


class Algorithm(metaclass=abc.ABCMeta):
    """
    Base class for algorithms
    """

    @abc.abstractmethod
    def train(self, bundle: Bundle):
        """Starts training the algorithm."""

    @property
    @abc.abstractmethod
    def is_trained(self) -> bool:
        """Returns true if the algorithm is trained."""

    @property
    @abc.abstractmethod
    def training_stats(self) -> Bundle:
        """Returns training stats."""

    @abc.abstractmethod
    def apply(self, bundle: Bundle) -> Bundle:
        """Applies the algorithm."""

    @property
    @abc.abstractmethod
    def trained_data_dir(self) -> str:
        """Returns the directory, where the trained data resides.

        This will be the new data application algorithms.

        """

    def close(self):
        """
        Close an algorithm (releasing resources, etc.)
        """
        pass
