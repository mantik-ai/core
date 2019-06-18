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
