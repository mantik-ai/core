from abc import abstractmethod
from mantik.types import Bundle


class TrainRequest:
    """
    A Request for training
    """

    @abstractmethod
    def train_data(self) -> Bundle:
        """
        The dataset for Training
        """

    @abstractmethod
    def finish_training(self, stats: Bundle, export_dir):
        """
        Finish the training with the stats
        :param stats: stats Mantik Bundle
        :param export_dir the dir where the trained model is written to.
        :return:
        """
