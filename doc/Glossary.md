# Glossary

All Terms are defined in the Way Mantik Core is using them

## Algorithm

A MantikItem which transforms input data to output data.

It is linked to a Bridge.

## Bridge

A special docker container which encapsulates a Framework for running a MantikItem.
Bridges itself are also MantikItems which can be referenced by others.

## DataSet

A MantikItem which generates Data, it has only one Output

It is linked to a Bridge.

## LocalRegistry

The local database, where Mantik Artifacts are stored.

## MantikArtifact

The data which is stored for a MantikItem, MantikHeader and optional Payload.
Sometimes this term is used like for MantikItems.

The term is used when referring to the local database.

## MantikItem

A logical block for the graphs which can be built using Mantik. 

## Pipeline

A MantikItem which consists of multiple Algorithms which are executed after each other.

## RemoteMantikRegistry

A registry where Mantik Engine can pull and push Mantik Artifacts to, in development.

## TrainableAlgorithm

A special MantikItem with one input and two outputs. The input corresponds to training data.
The first output to the trained payload, the second output to statistical data.

It is linked to two Bridges: The Bridge for Training and for Running the trained Payload.