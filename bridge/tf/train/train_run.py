import sys
from threading import Thread

import tensorflow as tf
from mantik import Bundle

import tftrain
from tftrain import TensorFlowTrainRequest
import traceback

class TrainRun:
    """
    Encapsulates the running of training in an extra thread.
    """

    # Training State
    train_started: bool = False
    train_finished: bool = False
    train_failure = None # Error message

    # Results of training
    train_stats = None
    train_export_dir = None
    train_stats = None


    def run(self, train_func, request, context: tftrain.TensorFlowContext):
        """
        Runs the training
        :param train_func a func taking request and context
        :param request: a TrainRequest instance
        :param context: context to be passed to train_func
        :return:
        """

        request.runner = self

        def runner():
            try:
                # Graph is property of the current thread.
                # We need the graph to be the same one, as we later collect data on it.
                with context.session.as_default():
                    with context.session.graph.as_default():
                        train_func(request, context)
                self.train_ended()
            except:
                traceback.print_exc()
                e = str(sys.exc_info()[0])
                self.training_error(e)
        thread = Thread(target=runner)
        thread.start()
        self.train_started = True
        return

    def training_error(self, error):
        print("Training error ", error)
        self.train_failure = error
        self.train_finished = True

    def finish_training(self, stats: Bundle, export_dir):
        if self.train_finished:
            print("Received train finish, when already finished")
            return

        self.train_stats = stats
        self.train_export_dir = export_dir
        self.train_finished = True

    def train_ended(self):
        if not self.train_finished:
            self.training_error("Algorithm finished without calling finish_training")



class TrainRequest(TensorFlowTrainRequest):

    runner: TrainRun

    # Training data
    train_data: Bundle
    session: tf.Session

    def __init__(self, session: tf.Session, train_data: Bundle):
        self.train_data = train_data
        self.session = session

    def set_run(self, run: TrainRun):
        self.runner = run

    def train_dataset(self) -> tf.data.Dataset:
        dataset = tftrain.bundle_to_dataset(self.train_data, self.session)
        return dataset

    def finish_training_with_dataset(self, stats: tf.data.Dataset, export_dir):
        bundle = tftrain.dataset_to_bundle(stats, self.session)
        return self.finish_training(bundle, export_dir)

    def finish_training(self, stats: Bundle, export_dir):
        self.runner.finish_training(stats, export_dir)

