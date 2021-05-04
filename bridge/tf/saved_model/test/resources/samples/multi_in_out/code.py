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

# A + B = Y
# Z = Maximum (Y)

# Dummy example which just multiplies an input value by 2

sess = tf.InteractiveSession()

a = tf.placeholder(tf.int64, [None, 3])
b = tf.placeholder(tf.int64, [None, 3])
x = tf.add(a, b)
y = tf.reduce_max(x, 1)

tf.global_variables_initializer().run()

# Exporting Trained Model
tf.saved_model.simple_save(
    sess, "./payload", inputs={"a": a, "b": b}, outputs={"x": x, "y": y}
)
