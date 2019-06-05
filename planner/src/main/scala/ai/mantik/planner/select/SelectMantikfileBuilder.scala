package ai.mantik.planner.select

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.repository.meta.MetaJson
import ai.mantik.planner.select.run.SelectProgram
import ai.mantik.repository.{ AlgorithmDefinition, MantikDefinition, Mantikfile }
import io.circe.syntax._

/** A Mantikfile which is compatible with the select bridge. */
case class SelectMantikfileBuilder(
    selectProgram: SelectProgram,
    `type`: FunctionType
) {

  def definition: AlgorithmDefinition = {
    AlgorithmDefinition(
      stack = "select",
      `type` = `type`
    )
  }

  def toMantikfile: Mantikfile[AlgorithmDefinition] = {
    val defJson = (definition: MantikDefinition).asJsonObject.add(
      "selectProgram", selectProgram.asJson
    )
    Mantikfile(definition, MetaJson.withoutMetaVariables(defJson))
  }
}
