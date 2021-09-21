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

import tensorflow as tf
from .context import Context
from mantik.types import MantikHeader


class TensorFlowContext(Context):
    session: tf.Session
    """
    The Tensor flow session
    """

    def __init__(self, mantikheader: MantikHeader, session: tf.Session):
        self.mantikheader = mantikheader
        self.session = session

    @staticmethod
    def local(session: tf.Session):
        """
        Create a local TensorFlowContext for testing without running the full bridge.
        :param session:
        :return:
        """
        # Assuning that script is started from data directory
        mf = MantikHeader.from_file("../MantikHeader")

        class LocalContext(TensorFlowContext):
            def __init__(self):
                TensorFlowContext.__init__(self, mf, session)

        ctxt = LocalContext()
        ctxt.session = session
        return ctxt
