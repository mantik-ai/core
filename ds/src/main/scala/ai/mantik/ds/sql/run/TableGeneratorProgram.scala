package ai.mantik.ds.sql.run

import ai.mantik.ds.TabularData
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.ds.sql.JoinType
import io.circe.{ Decoder, Encoder, Json, ObjectEncoder }
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

/**
 * A Program performing joins
 * Note: the join operation is usually splitted into this operation and two selects which prepares
 * the groups. See the Compiler.
 * @param left source for the left side, usually a select which also creates grouping
 * @param right source for the right side, usually a select which also creates grouping
 * @param groupSize prefix size of the groups, if 0 no grouping is performed.
 * @param joinType the join type
 * @param filter the filter applied to each possible left/right possible row.
 * @param selector selected columns to return (from concatenated left and right side, including groups)
 * @param result result tabular type
 */
case class JoinProgram(
    left: TableGeneratorProgram,
    right: TableGeneratorProgram,
    groupSize: Int,
    joinType: JoinType,
    filter: Option[Program] = None,
    selector: Vector[Int],
    result: TabularData
) extends TableGeneratorProgram

object TableGeneratorProgram {
  implicit val joinTypeEncoder: Encoder[JoinType] = Encoder { x: JoinType => Json.fromString(x.sqlName) }
  implicit val joinTypeDecoder: Decoder[JoinType] = Decoder.decodeString.emap { x =>
    JoinType.All.find(_.sqlName == x) match {
      case None     => Left(s"Unexpected join type ${x}")
      case Some(ok) => Right(ok)
    }
  }

  implicit val codec: ObjectEncoder[TableGeneratorProgram] with Decoder[TableGeneratorProgram] = new DiscriminatorDependentCodec[TableGeneratorProgram]("type") {
    override val subTypes = Seq(
      makeSubType[UnionProgram]("union"),
      makeSubType[SelectProgram]("select", true),
      makeSubType[DataSource]("source")
    )
  }
}
