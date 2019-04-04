package ai.mantik.repository

/** A Mantik Artefact. */
case class MantikArtefact(
    mantikfile: Mantikfile[_ <: MantikDefinition],
    fileId: Option[String],
    id: MantikId
)