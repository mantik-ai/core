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

import abc
import mantik.types

from . import bridge


class Algorithm(bridge.Bridge):
    """Abstract class for executable algorithms."""

    @abc.abstractmethod
    def apply(self, bundle: mantik.types.Bundle) -> mantik.types.Bundle:
        """Applies the algorithm."""


class TrainableAlgorithm(Algorithm):
    """Abstract class for trainable algorithms."""

    @abc.abstractmethod
    def train(self, bundle: mantik.types.Bundle):
        """Starts training the algorithm."""

    @property
    @abc.abstractmethod
    def is_trained(self) -> bool:
        """Returns true if the algorithm is trained."""

    @property
    @abc.abstractmethod
    def training_stats(self) -> mantik.types.Bundle:
        """Returns training stats."""

    @property
    @abc.abstractmethod
    def trained_data_dir(self) -> str:
        """Returns the directory, where the trained data resides.

        This will be the new data application algorithms.

        """
