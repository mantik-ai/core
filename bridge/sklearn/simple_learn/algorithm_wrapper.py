import os


# Wraps the supplied algorithm
class Algorithm(object):

    def __init__(self, directory: str):
        # TODO: I am pretty sure there is a nicer way to do so
        import sys
        sys.path.append(directory)
        import algorithm
        self.train_func = algorithm.train
        self.directory = directory
        self.try_init_func = algorithm.try_init
        self.apply_func = algorithm.apply
        self.is_trained = False
        self.model = None
        self.training_stats = None

    def train(self, bundle):
        old_pwd = os.getcwd()
        os.chdir(self.directory)
        try:
            stats = self.train_func(bundle)
            # This should now work and not catch
            self.model = self.try_init_func()
            print("Reinitialized after successful learn")
            self.training_stats = stats
            self.is_trained = True
            return stats
        finally:
            os.chdir(old_pwd)

    def try_init_catching(self):
        old_pwd = os.getcwd()
        os.chdir(self.directory)
        try:
            self.model = self.try_init_func()
            print("Successfully loaded Model...")
            self.is_trained = True
        except Exception as e:
            print("Could not load Model {}".format(e))
        finally:
            os.chdir(old_pwd)

    def apply(self, data):
        if not self.is_trained:
            raise Exception("Not trained")
        return self.apply_func(self.model, data)
