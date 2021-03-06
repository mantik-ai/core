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

from tensorflow.examples.tutorials.mnist import input_data
import tensorflow as tf
import mnist_beginner_model

mnist = input_data.read_data_sets("../sample_data/mnist/", one_hot=True)

model = mnist_beginner_model.create_model()


cross_entropy = tf.reduce_mean(
    tf.nn.softmax_cross_entropy_with_logits(labels=model.y_, logits=model.y)
)

train_step = tf.train.GradientDescentOptimizer(0.5).minimize(cross_entropy)

sess = tf.InteractiveSession()
tf.global_variables_initializer().run()

for _ in range(1000):
    batch_xs, batch_ys = mnist.train.next_batch(100)
    sess.run(train_step, feed_dict={model.x: batch_xs, model.y_: batch_ys})


correct_prediction = tf.equal(tf.argmax(model.y, 1), tf.argmax(model.y_, 1))
accuracy = tf.reduce_mean(tf.cast(correct_prediction, tf.float32))
print(
    sess.run(
        accuracy, feed_dict={model.x: mnist.test.images, model.y_: mnist.test.labels}
    )
)

# Exporting Trained Model
builder = tf.saved_model.builder.SavedModelBuilder("./trained_model")

# Annotating Input/Output
inputX = tf.saved_model.utils.build_tensor_info(model.x)
outputY = tf.saved_model.utils.build_tensor_info(model.y)

prediction_signature = tf.saved_model.signature_def_utils.build_signature_def(
    inputs={"x": inputX},
    outputs={"y": outputY},
    method_name=tf.saved_model.signature_constants.PREDICT_METHOD_NAME,
)

builder.add_meta_graph_and_variables(
    sess,
    tags=[tf.saved_model.tag_constants.SERVING],
    signature_def_map={
        "predict": prediction_signature,
        tf.saved_model.signature_constants.DEFAULT_SERVING_SIGNATURE_DEF_KEY: prediction_signature,
    },
)
builder.save()
