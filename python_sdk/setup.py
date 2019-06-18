import pathlib
from setuptools import setup, find_packages


def read(fname, split=True):
    with open(fname, "r") as f:
        content = f.read()
    return content.split("\n") if split else content


req = pathlib.Path(__file__).parent / "requirements.txt"

if __name__ == "__main__":
    setup(
        name="mantik",
        use_scm_version=True,
        description=__doc__,
        author="Mantik Team",
        author_email="info@mantik.ai",
        url="https://www.mantik.ai",
        install_requires=read(str(req)),
        python_requires=">=3.7",
        zip_safe=False,
        packages=find_packages(exclude=("tests",)),
    )
