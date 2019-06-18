import tensorflow as tf

# Dummy example which just multiplies an input value by 2

sess = tf.InteractiveSession()

x = tf.placeholder(tf.float64, [])
a = tf.Variable(tf.zeros([], dtype=tf.float64))
y = tf.multiply(x, a)

tf.global_variables_initializer().run()

sess.run(a.assign(2.0))

# Exporting Trained Model
tf.saved_model.simple_save(sess, "./trained_model", inputs={"x": x}, outputs={"y": y})
