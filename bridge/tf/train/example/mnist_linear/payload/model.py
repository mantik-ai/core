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


class Model:

    # Formular: Y' = (X matmul w) + b
    # Input x (height x width) of float32

    def __init__(self, X, Y, learning_rate, width, height):
        y_logits = tf.one_hot(Y, 10, axis=1)
        self.width = width
        self.height = height
        self.points = width * height
        self.w = tf.Variable(
            tf.random_normal(shape=[self.points, 10], stddev=0.01), name="weights"
        )
        self.b = tf.Variable(tf.zeros([1, 10]), name="bias")
        prepared_x = self.prepare_x(X)
        self.logits = tf.matmul(prepared_x, self.w) + self.b
        self.entropy = tf.nn.softmax_cross_entropy_with_logits_v2(
            logits=self.logits, labels=y_logits
        )
        self.loss = tf.reduce_mean(self.entropy)
        self.optimizer = tf.train.GradientDescentOptimizer(
            learning_rate=learning_rate
        ).minimize(self.loss)
        self.accuracy, self.accuracy_op = tf.metrics.accuracy(
            labels=Y, predictions=tf.argmax(self.logits, axis=1)
        )

    def prepare_x(self, x):
        return tf.reshape(x, shape=[-1, self.points])

    # Export the model, after it has been trained
    def export(self, sess: tf.Session, directory: str):
        # Note: saved_model bridge needs place holders to be functional.
        # We also do not want all the other stuff in the graph, so we put it into a new graph.
        (b_value, w_value) = sess.run((self.b, self.w))
        # print(b_value)
        # print(w_value)

        graph2 = tf.Graph()
        with graph2.as_default():
            xp = tf.placeholder(
                tf.float32, shape=[None, self.height, self.width], name="image"
            )
            xp_reshaped = self.prepare_x(xp)
            logits = tf.matmul(xp_reshaped, tf.constant(w_value)) + tf.constant(b_value)
            y = tf.dtypes.cast(tf.math.argmax(logits, axis=1), tf.uint8)
            tf.saved_model.simple_save(
                sess, directory, inputs={"x": xp}, outputs={"y": y, "logits": logits}
            )
