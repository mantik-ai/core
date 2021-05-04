#
# This file is part of the Mantik Project.
# Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
# Authors: See AUTHORS file
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License version 3.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.
#
# Additionally, the following linking exception is granted:
#
# If you modify this Program, or any covered work, by linking or
# combining it with other code, such other code is not for that reason
# alone subject to any of the requirements of the GNU Affero GPL
# version 3.
#
# You can be released from the requirements of the license by purchasing
# a commercial license.
#

from mantik.types import Bundle

from .train_request import TrainRequest
import tensorflow as tf
from abc import abstractmethod
from .tensorflow_conversion import dataset_to_bundle


class TensorFlowTrainRequest(TrainRequest):
    """
    A Training Request as being sent to tensor flow
    """

    @abstractmethod
    def train_dataset(self) -> tf.data.Dataset:
        """
        Returns the training data as Tensorflow Dataset
        :return:
        """

    @abstractmethod
    def finish_training_with_dataset(self, stats: tf.data.Dataset, export_dir):
        """
        Finish training by providing a dataset with stats.
        """

    @staticmethod
    def local(dataset: tf.data.Dataset, session: tf.Session):
        """
        Create a Tensorflow request from a local dataset for local testing
        :param dataset:
        :return:
        """

        class LocalTensorflowTrainRequest(TensorFlowTrainRequest):
            def train_dataset(self) -> tf.data.Dataset:
                return dataset

            def train_data(self) -> Bundle:
                raise NotImplementedError("Tensorflow should call train_dataset")

            def finish_training(self, stats: Bundle, export_dir):
                print("Tensorflow finished learning")
                print("Result     ", stats)
                print("Export Dir ", export_dir)
                pass

            def finish_training_with_dataset(self, stats: tf.data.Dataset, export_dir):
                self.finish_training(dataset_to_bundle(stats, session), export_dir)

        return LocalTensorflowTrainRequest()
