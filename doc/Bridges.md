# Mantik Bridge Specification


Bridges should abstract the inner implementation of a Machine Learning stack or format.

They are running as docker images.

They are talking using the MNP Protocol on Port 8501.

Data transport is done using [Mantik Bundles](DataTypes.md).


## Startup

- Bridges start up an MNP Server on Port 8501.
- They are responding to Mantik Init Session Requests and are loading their Payload and MantikHeader.
- After a session is initialized, new Data can be pushed and pulled from an MNP Bridge.

Bridges should not run as user root.

## Bridge Types

There are multiple bridge types corresponding to MantikItem types

- DataSet: Only one output, no input
- Algorithm: One Input, One Output
- TrainableAlgorithm: One Input (Training Data), Two outputs
  - Trained Algorithm Payload (as `application/zip`)
  - Stats (as `application/x-mantik-bundle`)


## MantikHeader

Bridges are MantikItems by theirself. Thus they have a MantikHeader which looks like this:

```
kind: bridge
account: mantik
name: binary
suitable:
    - dataset
dockerImage: mantikai/bridge.binary
protocol: 2
```

- `suitable` defines which kind of MantikItems the bridge supports, e.g. `trainable`, `dataset`, `algorithm`.
- `protocol` defines the Protocol version. The only supported protocol is `2`. Changes are reserved for future use.


## Guarantees

- Bridges can be sure that they receive input and output data in the form like it's presented 
  their Sessions' MantikHeader.
