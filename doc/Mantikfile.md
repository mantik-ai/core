# Mantikfile

The Mantikfile describes what a Mantik Artefact (DataSets, Algorithms, ...) is and how it will be handled by Mantik.

**Note:** The spec is not yet stable and subject of changes.

They are YAML or JSON Files and contain the following Keys:

- `kind` (optional) must be either `dataset`, `algorithm`, `trainable`, defaulting to `algorithm`. Describes what Kind of artefact it is.
- `metaVariables` variables which can be accessed by the bridges and which are interpolated into other values.
   (See [Meta Variables](#meta-variables))

## Version Related Fields

- Optional `name` The name of the artefact
- Optional `version` The version of the artefact
- Optional `author` The author of the artefact

These fields are used by the Mantik CLI Tool for easy uploading, but are otherwise ignored. It is possible for mantik to
name an artefact under a different name, than what is expressed in Mantikfile (e.g. on copying).

## Directory

Mantikfiles may contain an optional `directory` field which referes to a directory unter the Mantikfile. Upon uploading this
directory is serialized as ZIP-File and before application, this directory will be decompressed.

## Other fields

Other fields which are not required by the sub type are ignored by Mantik and forwared to the bridges.

## Data Type

Each Mantikfile must contain type-related information

### DataSet

A DataSet Mantikfile must contain a field `type` which declares its type.

It must contain a field `format` which maps to a format plugin.

### Algorithm

A Algorithm Mantikfile must contain a field `type` with sub fields `input` and `output` which declared input and output Data Types.

It must contain a field `stack` which maps to a algorithm plugin.

### Trainable Algorithm

A trainable algorithm must contain a field `trainingType` which contains the data type for training the algorithm.
Also it needs to have a field `statType` which contains the type of statistical output after training. However this type may be `void`.

It must also contain a `type` field which represents the type a trained algorithm has (with sub fields `input` and `output`, like for Algorithms).

It must contain a field `stack` which maps to a trainable algorithm plugin.


## Examples

Example for a Data Set definition

```
kind: dataset
name: mnist_test
format: binary
type:
  columns:
    x:
      type: image
      width: 28
      height: 28
      components:
        black:
          componentType: uint8
    label: uint8
directory: data
files:
  - file: t10k-labels-idx1-ubyte.gz
    compression: gzip
    skip: 8 # Magic Number and Length byte
    content:
      - element: label
      - stride: 1 # Could be auto detected, as this is the default for the element size
  - file: t10k-images-idx3-ubyte.gz
    compression: gzip
    skip: 16 # Magic Number and Length Byte
    content:
      - element: x
      - stride: 784 # Could be auto detected, as this is the default for the image size
```

Example for a Algorithm Definition:

```
directory: trained_model
name: double_multiply
stack: tf.saved_model
type:
  input:
    columns:
      x: float64
  output:
    columns:
      "y": float64 # Note: pure 'y' would be interpreted as "true" in YAML.
```

Example for a Trainable Algorithm Definition

```
name: kmeans
stack: sklearn.simple_learn
directory: code
kind: trainable

trainingType:
  columns:
    coordinates:
      type: tensor
      shape: [2]
      componentType: float64
statType: void
type:
  input:
    columns:
      coordinates:
        type: tensor
        shape: [2]
        componentType: float64
  output:
    columns:
      label: int32
```

### Meta Variables

A Mantik file can contain meta variables. This look like this:

```
name: my-algorithm
metaVariables:
  - name: problemSize
    type: int32
    value: 100
input:
  columns:
    x:
      type: tensor
      shape: ["${problemSize}"]
      componentType: float64
output: float32
```

The encoding of a simple meta variable is the same as the JSON-Encoding of a single value (See [DataTypes](DataTypes.md))
plus an extra `name`-Field.

This name can be referenced by a `"${name}"` String inside a Mantikfile's JSON or YAML.

Upon loading, all references will be interpolated, before data types are parsed. In the example
above the type of x would look like

```
type: tensor
shape: [100]
componentType: float64
```

This way, an algorithm can have Meta-Variable-specific input and output types.

**Fixed Meta Variables**

The planner is allowed to change the value of meta variables. In some cases this is not good
e.g. if an algorithm is the result of a trained algorithm. For that case, Meta variables
can have an extra attribute `fix` which accepts a boolen value.

In case of algorithms which are the result of a trainable algorithm, all meta variables
are automatically fixed.
