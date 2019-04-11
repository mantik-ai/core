package ai.mantik.repository

/** A Mantik Artefact. */
case class MantikArtifact(
    mantikfile: Mantikfile[_ <: MantikDefinition],
    fileId: Option[String],
    id: MantikId
)