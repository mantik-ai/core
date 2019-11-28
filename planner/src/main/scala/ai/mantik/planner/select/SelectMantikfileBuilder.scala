package ai.mantik.planner.select

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.{ AlgorithmDefinition, MantikDefinition, MantikHeader, Mantikfile }
import ai.mantik.elements.meta.MetaJson
import ai.mantik.planner.BuiltInItems
import ai.mantik.planner.select.run.SelectProgram
import io.circe.syntax._

/** A Mantikfile which is compatible with the select bridge. */
case class SelectMantikfileBuilder(
    selectProgram: SelectProgram,
    `type`: FunctionType
) {

  def definition: AlgorithmDefinition = {
    elements.AlgorithmDefinition(
      bridge = BuiltInItems.SelectBridgeName,
      `type` = `type`
    )
  }

  def toMantikfile: Mantikfile[AlgorithmDefinition] = {
    val defJson = (definition: MantikDefinition).asJsonObject.add(
      "selectProgram", selectProgram.asJson
    )
    Mantikfile(definition, MetaJson.withoutMetaVariables(defJson), MantikHeader())
  }
}
