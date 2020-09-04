# MantikHeader

The MantikHeader describes what a Mantik Artefact (DataSets, Algorithms, ...) is and how it will be handled by Mantik.

They are YAML or JSON Files and contain the following Keys:

- `kind` (optional) must be either `dataset`, `algorithm`, `trainable`, `bridge`, defaulting to `algorithm`. Describes what Kind of artefact it is.
- `metaVariables` variables which can be accessed by the bridges and which are interpolated into other values.
   (See [Meta Variables](#meta-variables))

## Version Related Fields

- Optional `name` The name of the artefact
- Optional `version` The version of the artefact
- Optional `author` The author of the artefact

These fields are used by the Mantik CLI Tool for easy uploading, but are otherwise ignored. It is possible for mantik to
name an artefact under a different name, than what is expressed in MantikHeader (e.g. on copying).

## Payload

Mantik Artifacts may contain payload content. On session-initialization it is unpacked by the bridge.

Bridges do not have payload.

## Bridges

Mantik Artifacts which are used together with Bridges must have a field `bridge`, which names the
Name of the Bridge.


## Other fields

Other fields which are not required by the sub type are ignored by Mantik and forwarded to the bridges.

## Data Type

Each MantikHeader must contain type-related information, which is dependent on the Artifact Type

### DataSet

A DataSet MantikHeader must contain a field `type` which declares its type.

### Algorithm

A Algorithm MantikHeader must contain a field `type` with sub fields `input` and `output` which declared input and output Data Types.

### Trainable Algorithm

A trainable algorithm must contain a field `trainingType` which contains the data type for training the algorithm.
Also it needs to have a field `statType` which contains the type of statistical output after training. However this type may be `void`.

It must also contain a `type` field which represents the type a trained algorithm has (with sub fields `input` and `output`, like for Algorithms).

## Examples

- Example for a Data Set definition: `bridge/binary/test/mnist/MantikHeader`
- Example for an Algorithm definition: `bridge/tf/saved_model/test/resources/samples/double_multiply/MantikHeader`
- Example for a Trainable definition: `bridge/binary/test/mnist/MantikHeader`

### Meta Variables

A Mantik Header can contain meta variables. This look like this:

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

This name can be referenced by a `"${name}"` String inside a MantikHeader's JSON or YAML.

Upon loading, all references will be interpolated, before data types are parsed. In the example
above the type of x would look like

```
type: tensor
shape: [100]
componentType: float64
```

This way, an algorithm can have Meta-Variable-specific input and output types.

**Fixed Meta Variables**

The planner is allowed to change the value of meta variables. In some cases this is not a good idea
e.g. if an algorithm is the result of a trained algorithm. For that case, Meta variables
can have an extra attribute `fix` which accepts a boolean value.

In case of algorithms which are the result of a trainable algorithm, all meta variables
are automatically fixed.
