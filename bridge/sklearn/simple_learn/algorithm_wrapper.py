import os
from mantik.bridge import Algorithm
from mantik import Mantikfile

# Wraps the supplied algorithm
class AlgorithmWrapper(Algorithm):

    def __init__(self, mantikfile: Mantikfile):
        # TODO: I am pretty sure there is a nicer way to do so
        import sys
        sys.path.append(mantikfile.payload_dir())
        import algorithm
        self.train_func = algorithm.train
        self.try_init_func = algorithm.try_init
        self.apply_func = algorithm.apply
        self.is_trained_status = False
        self.model = None
        self.training_stats = None
        self.mantikfile = mantikfile

    def is_trained(self) -> bool:
        return self.is_trained_status

    def trained_data_dir(self) -> str:
        return self.mantikfile.payload_dir()

    def train(self, bundle):
        old_pwd = os.getcwd()
        os.chdir(self.mantikfile.payload_dir())
        try:
            stats = self.train_func(bundle)
            # This should now work and not catch
            self.model = self.try_init_func()
            print("Reinitialized after successful learn")
            self.training_stats = stats
            self.is_trained_status = True
            return stats
        finally:
            os.chdir(old_pwd)

    def try_init_catching(self):
        old_pwd = os.getcwd()
        os.chdir(self.mantikfile.payload_dir())
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
