import setuptools

NAME = 'mantik'
VERSION = '0.0.0'
MIN_PYTHON = '>=3.7.0'

setuptools.setup(
    name = NAME,
    version = VERSION,
    python_requires = MIN_PYTHON,
    install_requires=(
        'pyyaml',
        'msgpack',
        'flask'
    )
)
