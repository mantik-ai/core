import tensorflow as tf

class Model:

    # Formular: Y' = (X matmul w) + b
    # Input x (28x28) of float32

    def __init__(self, X, Y, learning_rate):
        y_logits = tf.one_hot(Y, 10, axis=1)
        self.w = tf.Variable(tf.random_normal(shape=[784, 10], stddev=0.01), name="weights")
        self.b = tf.Variable(tf.zeros([1, 10]), name="bias")
        prepared_x = Model.prepare_x(X)
        self.logits = tf.matmul(prepared_x, self.w) + self.b
        self.entropy = tf.nn.softmax_cross_entropy_with_logits_v2(logits=self.logits, labels=y_logits)
        self.loss = tf.reduce_mean(self.entropy)
        self.optimizer = tf.train.GradientDescentOptimizer(learning_rate=learning_rate).minimize(self.loss)
        self.accuracy, self.accuracy_op = tf.metrics.accuracy(labels=Y, predictions=tf.argmax(self.logits, axis=1))

    @staticmethod
    def prepare_x(x):
        return tf.reshape(x, shape=[-1, 784])

    # Export the model, after it has been trained
    def export(self, sess: tf.Session, directory: str):
        # Note: saved_model bridge needs place holders to be functional.
        # We also do not want all the other stuff in the graph, so we put it into a new graph.
        (b_value, w_value) = sess.run((self.b, self.w))
        # print(b_value)
        # print(w_value)

        graph2 = tf.Graph()
        with graph2.as_default():
            xp = tf.placeholder(tf.float32, shape=[None, 28, 28], name="image")
            xp_reshaped = Model.prepare_x(xp)
            logits = tf.matmul(xp_reshaped, tf.constant(w_value)) + tf.constant(b_value)
            y = tf.dtypes.cast(tf.math.argmax(logits, axis=1), tf.uint8)
            tf.saved_model.simple_save(
                sess,
                directory,
                inputs={"x": xp},
                outputs={"y": y}
            )


