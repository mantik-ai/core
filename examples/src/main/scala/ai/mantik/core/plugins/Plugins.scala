package ai.mantik.core.plugins

/** Contains multiple plugins for data formats. */
class Plugins(
    formatPlugins: Seq[FormatPlugin],
    algorithmPlugins: Seq[AlgorithmPlugin]
) {

  /** Resolves a plugin. */
  def pluginForFormat(format: String): Option[FormatPlugin] = {
    formatPlugins.find(_.format == format)
  }

  def pluginForAlgorithm(stack: String): Option[AlgorithmPlugin] = {
    algorithmPlugins.find(_.stack == stack)
  }
}

object Plugins {

  def default: Plugins = new Plugins(
    Seq(NaturalFormatPlugin),
    Seq(TensorFlowSavedModelPlugin)
  )
}