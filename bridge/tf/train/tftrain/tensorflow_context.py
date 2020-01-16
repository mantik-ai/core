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
        mf = MantikHeader.load("../MantikHeader")

        class LocalContext(TensorFlowContext):
            def __init__(self):
                TensorFlowContext.__init__(self, mf, session)

        ctxt = LocalContext()
        ctxt.session = session
        return ctxt
