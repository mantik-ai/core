Mantik Ids
==========

In order to find Mantik Artifacts (or Items), Mantik is using so called Mantik Ids.

They correspond roughly to Docker Image names/tags, but have some slight differences.

Their basic format is as follows:

```
account/name:version
```

If version is omitted, then it defaults to `latest`. The version behaves like docker image tags.

If account is omitted, then it defaults to `library`. 

The version has no further semantic meaning, their may be a version `v2`, which can be newer than `latest`.

Artifacts in a repository can be overwritten by a newer revision of an artifact.

## Item Id

As Artifacts can be overwritten, each immutable Artifact has a so called random `ItemId`. This item id
is immutable. There may not be two items of the same `ItemId`.

In the moment, they are created randomly by Mantik Core (see Issue #110).

## Anonymous Mantik Ids

There are items who don't have a name, but may be referenced by other Artifacts (e.g. Pipelines). They get
an anonymous Mantik Id of the form `@itemId`, without any account or version.
