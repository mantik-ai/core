package ai.mantik.planner.select

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.planner.select.run.SelectProgram
import ai.mantik.repository.{ AlgorithmDefinition, MantikDefinition, Mantikfile }
import io.circe.Json
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
    val defJson = (definition: MantikDefinition).asJson.deepMerge(Json.obj(
      "selectProgram" -> selectProgram.asJson
    ))
    Mantikfile(definition, defJson)
  }
}
