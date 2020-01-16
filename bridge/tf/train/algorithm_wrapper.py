from mantik.bridge import Algorithm
from mantik.types import MantikHeader, Bundle
from tftrain import TensorFlowContext
import tensorflow as tf
import tftrain
from train_run import TrainRequest, TrainRun
import os


class AlgorithmWrapper(Algorithm):

    session: tf.Session  # According to docu, the session is threadsafe
    context: TensorFlowContext

    runner: TrainRun

    def __init__(self, mantikheader: MantikHeader):
        self.runner = TrainRun()
        self.session = tf.Session()
        import sys

        sys.path.append(mantikheader.payload_dir)
        import train  # Entry point

        self.train_func = train.train
        self.context = TensorFlowContext(mantikheader, self.session)

        # Override working directory, so that Algorithm is in it's own
        os.chdir(mantikheader.payload_dir)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def train(self, bundle: Bundle):
        if self.runner.train_started:
            raise Exception("Train already started")

        request = TrainRequest(self.session, bundle)
        self.runner.run(self.train_func, request, self.context)
        return

    @property
    def is_trained(self) -> bool:
        return self.runner.train_finished

    @property
    def trained_data_dir(self) -> str:
        if not self.runner.train_finished:
            raise Exception("Training not finished")
        if self.runner.train_failure is not None:
            raise Exception("Training failed ", self.runner.train_failure)
        return self.runner.train_export_dir

    @property
    def training_stats(self) -> Bundle:
        if not self.runner.train_finished:
            raise Exception("Training not finished")
        if self.runner.train_failure is not None:
            raise Exception("Training failed ", self.runner.train_failure)

        return self.runner.train_stats

    def close(self):
        self.session.close()

    def apply(self, bundle: Bundle) -> Bundle:
        pass
