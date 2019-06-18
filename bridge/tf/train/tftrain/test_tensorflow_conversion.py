from .tensorflow_conversion import bundle_to_dataset, dataset_to_bundle
import tensorflow as tf
import numpy as np
from mantik.types import DataType, Bundle
from io import BytesIO

sample_type = DataType.from_json(
    """
{
    "columns": {
        "x": {
            "type":"tensor",
            "componentType": "int32",
            "shape":[]
        },
        "y": {
            "type": "tensor",
            "componentType": "string",
            "shape":[]
        }
    }
}
"""
)

complex_tensor = DataType.from_json(
    """
{
    "columns": {
        "x": {
            "type":"tensor",
            "componentType": "float32",
            "shape":[2, 3]
        }
    }
}
"""
)


def test_empty_to_dataset():
    with tf.Session() as sess:
        data = Bundle(value=[], type=sample_type)
        dataset = bundle_to_dataset(data, sess)
        it = dataset.make_one_shot_iterator()
        next = it.get_next()
        ok = False
        try:
            sess.run(next)
        except tf.errors.OutOfRangeError:
            ok = True
        assert ok


def test_to_dataset():
    with tf.Session() as sess:
        data = Bundle(value=[[[1], ["Hello"]], [[2], ["World"]]], type=sample_type)
        dataset = bundle_to_dataset(data, sess)

        it = dataset.make_one_shot_iterator()
        next = it.get_next()
        assert sess.run(next) == (1, b"Hello")
        assert sess.run(next) == (2, b"World")


def test_to_dataset_deep_tensor():
    with tf.Session() as sess:
        # Mantik is using packed values.
        data = Bundle(
            value=[
                [[1.0, 2.0, 3.0, 4.0, 5.0, 6.0]],
                [[7.0, 8.0, 9.0, 10.00, 11.0, 12.0]],
            ],
            type=complex_tensor,
        )

        dataset = bundle_to_dataset(data, sess)
        it = dataset.make_one_shot_iterator()
        next = it.get_next()
        assert sess.run(next)[0].tolist() == [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]
        assert sess.run(next)[0].tolist() == [[7.0, 8.0, 9.0], [10.0, 11.0, 12.0]]


def test_to_dataset_scalars():
    with tf.Session() as sess:
        data = Bundle(
            value=[[1], [2], [3]],
            type=DataType.from_json('{"columns":{"x":"int32"}}'),
        )
        dataset = bundle_to_dataset(data, sess)
        it = dataset.make_one_shot_iterator()
        next = it.get_next()

        assert sess.run(next)[0] == 1
        assert sess.run(next)[0] == 2
        assert sess.run(next)[0] == 3


def test_to_mantik():
    with tf.Session() as sess:
        a = np.array([(1, "Hello"), (2, "World")])
        tensor1 = tf.constant(value=[1.0, 2.0, 3.0], dtype=tf.float32)
        tensor2 = tf.constant(value=["A", "B", "C"], dtype=tf.string)

        dataset = tf.data.Dataset.from_tensor_slices((tensor1, tensor2))

        bundle = dataset_to_bundle(dataset, sess)
        assert bundle.type is None
        assert bundle.value == [[[1.0], [b"A"]], [[2.0], [b"B"]], [[3.0], [b"C"]]]

        # The generated value must be serializable
        mp = bundle.encode_msgpack()
        back = Bundle.decode_msgpack(BytesIO(mp))
        # (Note: it's loosing the byte strings during that process)
        assert back.value == [[[1.0], ["A"]], [[2.0], ["B"]], [[3.0], ["C"]]]


def test_to_mantik_deep_tensor():
    with tf.Session() as sess:
        value = [
            [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]],
            [[7.0, 8.0, 9.0], [10.0, 11.0, 12.0]],
        ]
        tensor = tf.constant(value=value, dtype=tf.float32)
        dataset = tf.data.Dataset.from_tensor_slices(tensor)
        bundle = dataset_to_bundle(dataset, sess)
        assert bundle.type is None
        assert bundle.value == [
            [[1.0, 2.0, 3.0, 4.0, 5.0, 6.0]],
            [[7.0, 8.0, 9.0, 10.0, 11.0, 12.0]],
        ]

        # The generated value must be serializable
        mp = bundle.encode_msgpack()
        back = Bundle.decode_msgpack(BytesIO(mp))
        assert back == bundle
