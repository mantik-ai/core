from mantik.types import DataType, Bundle
import tensorflow as tf
import numpy as np


def bundle_to_dataset(bundle: Bundle, session: tf.Session) -> tf.data.Dataset:
    if bundle.type is None:
        raise Exception("bundle must have data type")

    if not bundle.type.is_tabular:
        raise Exception(
            "Only tabular bundles supported, type is " + bundle.type.to_json()
        )

    columns = bundle.type.column_names
    column_tensors = []
    for c in columns:
        tensor_converted = __convert_column_to_tensor(bundle, c)
        column_tensors.append(tensor_converted)

    return tf.data.Dataset.from_tensor_slices(tuple(column_tensors))


def __convert_column_to_tensor(bundle: Bundle, column: str) -> tf.Tensor:
    column_type = bundle.type.column_type(column)
    dtype = __dtype(column_type)

    if column_type.sub_type == "fundamental":
        base_shape = []
    else:
        base_shape = column_type.representation.get("shape")
    shape = [len(bundle)] + base_shape
    values = bundle.flat_column(column)
    return tf.constant(values, dtype=dtype, shape=shape)


def __dtype(data_type: DataType):
    st = data_type.sub_type
    if st == "fundamental":
        return dTypeMapping.get(data_type.representation)
    if st != "tensor":
        raise Exception("Only tensors / fundamentals allowed, got {}".format(st))
    componentType = data_type.representation.get("componentType")
    return dTypeMapping.get(componentType)


dTypeMapping = {
    "uint8": tf.uint8,
    "int8": tf.int8,
    "uint32": tf.uint32,
    "int32": tf.int32,
    "uint64": tf.uint64,
    "int64": tf.int64,
    "float32": tf.float32,
    "float64": tf.float64,
    "string": tf.string,
    "bool": tf.bool,
}


def dataset_to_bundle(dataset: tf.data.Dataset, session: tf.Session) -> Bundle:
    iterator = dataset.make_one_shot_iterator()
    tensor = iterator.get_next()
    reshaped = __reshape_to_flat(tensor)
    rows = []
    try:
        while True:
            row_builder = []
            row = session.run(reshaped)
            # TODO Optimize Me!
            if isinstance(row, tuple):
                for value in row:
                    if isinstance(value, np.ndarray):
                        row_builder.append(value.tolist())
                    else:
                        row_builder.append(value)
            else:
                if isinstance(row, np.ndarray):
                    row_builder.append(row.tolist())
                else:
                    row_builder.append(row)

            rows.append(row_builder)
    except tf.errors.OutOfRangeError:
        pass
    return Bundle(value=rows)


# Reshape a tensor or tuple of tensors into flat values.
def __reshape_to_flat(t):
    if isinstance(t, tuple):
        r = []
        for i in t:
            r.append(__reshape_to_flat(i))
        return tuple(r)
    else:
        return tf.reshape(t, shape=[-1])
