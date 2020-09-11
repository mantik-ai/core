# Glossary

All Terms are defined in the Way Mantik Core is using them

## Action

Something the planner (and with it, the Engine) can execute. It contains all necessary dependencies, is serializable
and changes state (e.g. using many resources or deploying/saving something).

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

A logical block for the graphs which can be built using Mantik. Using combinators (e.g. `apply`) it can be comined 
with other Mantik Items. Mantik Items are immutable and do not change state (in contrast to Actions).

## Pipeline

A MantikItem which consists of multiple Algorithms which are executed after each other.

## RemoteMantikRegistry

A registry where Mantik Engine can pull and push Mantik Artifacts to, in development.

## TrainableAlgorithm

A special MantikItem with one input and two outputs. The input corresponds to training data.
The first output to the trained payload, the second output to statistical data.

It is linked to two Bridges: The Bridge for Training and for Running the trained Payload.