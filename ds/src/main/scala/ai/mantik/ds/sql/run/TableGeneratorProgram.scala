package ai.mantik.ds.sql.run

import ai.mantik.ds.TabularData
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import io.circe.{ Decoder, Encoder, ObjectEncoder }
import io.circe.generic.semiauto

/**
 * A Program for genearting (temporary tables)
 * Note: this is part of the API to the Select Bridge.
 */
sealed trait TableGeneratorProgram {
  /** Result Data Type. */
  def result: TabularData
}

/**
 * A compiled Select Statement.
 *
 * @param input where the data comes from (default to port 0)
 * @param selector a program doing the selection. called with a row, returns bool on the stack if it succeeds. If empty, the row is always selected.
 * @param projector a program doing the projection. called with a row, returns the translated row. If empty, the row is returned untouched.
 */
case class SelectProgram(
    input: Option[TableGeneratorProgram] = None,
    selector: Option[Program],
    projector: Option[Program],
    result: TabularData
) extends TableGeneratorProgram

/** An input source. */
case class DataSource(
    port: Int,
    result: TabularData
) extends TableGeneratorProgram {
  require(port >= 0, "Port must be >= 0")
}

/**
 * A Program for performing unions.
 *
 * @param inputs different inputs for the program.
 * @param all if true emit all rows
 * @param inOrder extension, emit result from left to right (makes it order deterministic)
 */
case class UnionProgram(
    inputs: Vector[TableGeneratorProgram],
    all: Boolean,
    result: TabularData,
    inOrder: Boolean
) extends TableGeneratorProgram

object TableGeneratorProgram {
  implicit val codec: ObjectEncoder[TableGeneratorProgram] with Decoder[TableGeneratorProgram] = new DiscriminatorDependentCodec[TableGeneratorProgram]("type") {
    override val subTypes = Seq(
      makeSubType[UnionProgram]("union"),
      makeSubType[SelectProgram]("select", true),
      makeSubType[DataSource]("source")
    )
  }
}