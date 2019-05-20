from abc import ABC
from mantik import Bundle

class Algorithm(ABC):
    """
    Base class for algorithms
    """

    def train(self, bundle: Bundle):
        """
        Starts training the algorithm
        """
        raise NotImplementedError

    def is_trained(self) -> bool:
        """
        Returns true if the algorithm is trained.
        """
        raise NotImplementedError

    def training_stats(self) -> Bundle:
        """
        Returns training stats
        """
        return NotImplementedError

    def apply(self, bundle: Bundle) -> Bundle:
        """
        Applies the algorithm
        """
        raise NotImplementedError

    def trained_data_dir(self) -> str:
        """
        Returns the directory, where the trained data resides. This will be the new data
        application algorithms
        """
        raise NotImplementedError





