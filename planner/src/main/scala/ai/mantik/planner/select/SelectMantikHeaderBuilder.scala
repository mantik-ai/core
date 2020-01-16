package ai.mantik.planner.select

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.{ AlgorithmDefinition, MantikDefinition, MantikHeaderMeta, MantikHeader }
import ai.mantik.elements.meta.MetaJson
import ai.mantik.planner.BuiltInItems
import ai.mantik.planner.select.run.SelectProgram
import io.circe.syntax._

/** Converts a [[SelectProgram]] to a [[MantikHeader]] for the select-Bridge. */
case class SelectMantikHeaderBuilder(
    selectProgram: SelectProgram,
    `type`: FunctionType
) {

  def definition: AlgorithmDefinition = {
    elements.AlgorithmDefinition(
      bridge = BuiltInItems.SelectBridgeName,
      `type` = `type`
    )
  }

  def toMantikHeader: MantikHeader[AlgorithmDefinition] = {
    val defJson = (definition: MantikDefinition).asJsonObject.add(
      "selectProgram", selectProgram.asJson
    )
    MantikHeader(definition, MetaJson.withoutMetaVariables(defJson), MantikHeaderMeta())
  }
}
