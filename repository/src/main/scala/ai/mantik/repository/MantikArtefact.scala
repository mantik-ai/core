package ai.mantik.repository

/** A Mantik Artefact. */
case class MantikArtefact(
    mantikfile: Mantikfile[MantikDefinition],
    fileId: Option[String]
) {
  def id: MantikId = MantikId(mantikfile.definition.name, mantikfile.definition.version)
}