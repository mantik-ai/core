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

# Holds Model Variables.
class Model:
    pass


# Create Model on an implicit Graph.
def create_model():
    model = Model()
    model.x = tf.placeholder(tf.float32, [None, 784], name="x")

    model.W = tf.Variable(tf.zeros([784, 10]))
    model.b = tf.Variable(tf.zeros([10]))

    model.y = tf.nn.softmax(tf.matmul(model.x, model.W) + model.b, name="y")

    model.y_ = tf.placeholder(tf.float32, [None, 10])

    return model
