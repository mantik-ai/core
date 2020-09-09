package ai.mantik.planner.select

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.sql.Select
import ai.mantik.elements
import ai.mantik.elements.{ AlgorithmDefinition, MantikDefinition, MantikHeader, MantikHeaderMeta }
import ai.mantik.elements.meta.MetaJson
import ai.mantik.planner.BuiltInItems
import ai.mantik.ds.sql.run.{ Compiler, SelectProgram }
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

object SelectMantikHeaderBuilder {

  /**
   * Compile select statement to a select mantikHeader.
   * @return either an error or a mantikHeader which can execute the selection.
   */
  def compileSelectToMantikHeader(select: Select): Either[String, MantikHeader[AlgorithmDefinition]] = {
    val selectProgram = Compiler.compile(select)
    selectProgram.map { program =>

      val functionType = FunctionType(
        select.inputType,
        select.resultingType
      )

      SelectMantikHeaderBuilder(program, functionType).toMantikHeader
    }
  }
}
