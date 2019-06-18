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
    sess, "./trained_model", inputs={"a": a, "b": b}, outputs={"x": x, "y": y}
)
