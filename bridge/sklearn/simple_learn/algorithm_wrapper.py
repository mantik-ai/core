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

import os
from mantik.bridge import Algorithm
from mantik.types import MantikHeader, Bundle


# Wraps the supplied algorithm
class AlgorithmWrapper(Algorithm):
    def __init__(self, mantikheader: MantikHeader):
        # TODO: I am pretty sure there is a nicer way to do so
        import sys

        sys.path.append(mantikheader.payload_dir)
        import algorithm

        self.train_func = algorithm.train
        self.try_init_func = algorithm.try_init
        self.apply_func = algorithm.apply
        self.is_trained_status = False
        self.model = None
        self.training_stats_result = None
        self.mantikheader = mantikheader

    @property
    def is_trained(self) -> bool:
        return self.is_trained_status

    @property
    def trained_data_dir(self) -> str:
        return self.mantikheader.payload_dir

    def train(self, bundle):
        old_pwd = os.getcwd()
        os.chdir(self.mantikheader.payload_dir)
        try:
            stats = self.train_func(bundle, self.mantikheader.meta_variables)
            # This should now work and not catch
            self.model = self.try_init_func()
            print("Reinitialized after successful learn")
            self.training_stats_result = stats
            self.is_trained_status = True
            return stats
        finally:
            os.chdir(old_pwd)

    @property
    def training_stats(self) -> Bundle:
        return self.training_stats_result

    def try_init_catching(self):
        old_pwd = os.getcwd()
        os.chdir(self.mantikheader.payload_dir)
        try:
            self.model = self.try_init_func()
            print("Successfully loaded Model...")
            self.is_trained_status = True
        except Exception as e:
            print("Could not load Model {}".format(e))
        finally:
            os.chdir(old_pwd)

    def apply(self, data):
        if not self.is_trained_status:
            raise Exception("Not trained")
        return self.apply_func(self.model, data)
