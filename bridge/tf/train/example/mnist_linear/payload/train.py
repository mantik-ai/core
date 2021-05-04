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
from tftrain import TensorFlowContext, TensorFlowTrainRequest
from model import Model
from mantik.types import Bundle


def train(request: TensorFlowTrainRequest, context: TensorFlowContext):
    train_dataset = request.train_dataset()
    # Meta Variables

    batch_size = context.mantikheader.meta_variables.get("batch_size", 128)
    n_epochs = context.mantikheader.meta_variables.get("n_epochs", 5)
    learning_rate = context.mantikheader.meta_variables.get("learning_rate", 0.01)
    width = context.mantikheader.meta_variables.get("width", 28)
    height = context.mantikheader.meta_variables.get("height", 28)

    stats = []
    batches = train_dataset.batch(batch_size)
    iterator = batches.make_initializable_iterator()

    data_x, data_y = iterator.get_next()
    model = Model(data_x, data_y, learning_rate, width, height)

    sess = context.session
    sess.run(tf.global_variables_initializer())
    sess.run(tf.local_variables_initializer())
    sess.run(iterator.initializer)

    for epoch in range(n_epochs):
        sess.run(iterator.initializer)

        try:
            while True:
                _, current_loss = sess.run([model.optimizer, model.loss])
        except tf.errors.OutOfRangeError:
            pass

        print("Epoch ", epoch, " of ", n_epochs, " loss=", current_loss)
        stats.append([epoch, current_loss.item()])

    # Calculating Accuracy
    sess.run(iterator.initializer)
    try:
        while True:
            sess.run([model.accuracy_op])
    except tf.errors.OutOfRangeError:
        pass
    accuracy = sess.run(model.accuracy)
    print("Accuracy: {}".format(accuracy))

    dir = "trained_model"
    model.export(context.session, dir)
    request.finish_training(Bundle(value=stats), dir)


def create_local_mnist_dataset() -> tf.data.Dataset:
    mnist_train, _ = (
        tf.keras.datasets.mnist.load_data()
    )  # Ignore test dataset, Mantik won't give you that too

    mnist_train_x, mnist_train_y = mnist_train

    train_dataset = tf.data.Dataset.from_tensor_slices((mnist_train_x, mnist_train_y))

    train_dataset = train_dataset.map(lambda x, y: (tf.dtypes.cast(x, tf.float32), y))
    return train_dataset


if __name__ == "__main__":
    with tf.Session() as sess:
        dataset = create_local_mnist_dataset()
        context = TensorFlowContext.local(sess)
        request = TensorFlowTrainRequest.local(dataset, sess)
        train(request, context)
