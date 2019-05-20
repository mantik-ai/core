from algorithm_wrapper import AlgorithmWrapper
from mantik import Mantikfile, Bundle
import os
import time

def test_simple_training():
    dir = "example/factor"
    mf = Mantikfile.load(os.path.join(dir, "Mantikfile"))

    with AlgorithmWrapper(mf) as wrapper:
        assert not wrapper.is_trained()

        train_data = Bundle(value=[[1.0, 2.0], [2.0, 4.0]], data_type=mf.training_type)
        wrapper.train(train_data)

        wait_for(wrapper.is_trained)

        assert wrapper.is_trained()

        stats = wrapper.training_stats()
        assert len(stats.value) == 1
        assert stats.value[0][0][0] >= 1.9
        assert stats.value[0][0][0] <= 2.1

        assert wrapper.trained_data_dir() == "my_export_dir"




def wait_for(f):
    timeout = time.time() + 10
    while time.time() < timeout:
        if f():
            return
        time.sleep(1)
    raise TimeoutError()