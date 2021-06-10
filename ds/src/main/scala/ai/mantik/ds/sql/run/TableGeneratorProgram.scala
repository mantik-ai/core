/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.ds.sql.run

import ai.mantik.ds.TabularData
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.ds.sql.JoinType
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.circe.generic.semiauto

import java.util.Locale

/**
  * A Program for genearting (temporary tables)
  * Note: this is part of the API to the Select Bridge.
  */
sealed trait TableGeneratorProgram {

  /** Result Data Type. */
  def result: TabularData

  /** Maximum id of input sources */
  def maxInputSource: Int

  /** Extra results (in case of [[MultiTableGeneratorProgram]]) */
  def extraResults: Vector[TabularData]

  /** Return the type of all result tables */
  def allResults: Vector[TabularData] = result +: extraResults
}

/** Special program for generating multiple tabular output values. */
sealed trait MultiTableGeneratorProgram extends TableGeneratorProgram

/** A Program which emits exactly one table */
sealed trait SingleTableGeneratorProgram extends TableGeneratorProgram {
  override final def extraResults: Vector[TabularData] = Vector.empty
}

/**
  * A compiled Select Statement.
  *
  * @param input where the data comes from (default to port 0)
  * @param selector a program doing the selection. called with a row, returns bool on the stack if it succeeds. If empty, the row is always selected.
  * @param projector a program doing the projection. called with a row, returns the translated row. If empty, the row is returned untouched.
  */
case class SelectProgram(
    input: Option[SingleTableGeneratorProgram] = None,
    selector: Option[Program],
    projector: Option[Program],
    result: TabularData
) extends SingleTableGeneratorProgram {
  override def maxInputSource: Int = input.map(_.maxInputSource).getOrElse(0)
}

/** An input source. */
case class DataSource(
    port: Int,
    result: TabularData
) extends SingleTableGeneratorProgram {
  require(port >= 0, "Port must be >= 0")

  override def maxInputSource: Int = port
}

/**
  * A Program for performing unions.
  *
  * @param inputs different inputs for the program.
  * @param all if true emit all rows
  * @param inOrder extension, emit result from left to right (makes it order deterministic)
  */
case class UnionProgram(
    inputs: Vector[SingleTableGeneratorProgram],
    all: Boolean,
    result: TabularData,
    inOrder: Boolean
) extends SingleTableGeneratorProgram {
  override def maxInputSource: Int = inputs.map(_.maxInputSource).max
}

/**
  * A Program performing joins
  * Note: the join operation is usually splitted into this operation and two selects which prepares
  * the groups. See the Compiler.
  * @param left source for the left side, usually a select which also creates grouping
  * @param right source for the right side, usually a select which also creates grouping
  * @param groupSize prefix size of the groups, if 0 no grouping is performed.
  * @param joinType the join type. Encoding "inner", "left", "right", "outer"
  * @param filter the filter applied to each possible left/right possible row.
  * @param selector selected columns to return (from concatenated left and right side, including groups)
  * @param result result tabular type
  */
case class JoinProgram(
    left: SingleTableGeneratorProgram,
    right: SingleTableGeneratorProgram,
    groupSize: Int,
    joinType: JoinType,
    filter: Option[Program] = None,
    selector: Vector[Int],
    result: TabularData
) extends SingleTableGeneratorProgram {
  override def maxInputSource: Int = left.maxInputSource.max(right.maxInputSource)
}

/** A program for splitting input streams. */
case class SplitProgram(
    input: SingleTableGeneratorProgram,
    fractions: Vector[Double],
    shuffleSeed: Option[Long]
) extends MultiTableGeneratorProgram {
  override def extraResults: Vector[TabularData] = Vector.fill(fractions.size)(input.result)

  override def result: TabularData = input.result

  override def maxInputSource: Int = input.maxInputSource
}

object TableGeneratorProgram {

  val joinTypeMapping = Seq(
    JoinType.Inner -> "inner",
    JoinType.Left -> "left",
    JoinType.Right -> "right",
    JoinType.Outer -> "outer"
  )

  implicit val joinTypeEncoder: Encoder[JoinType] = Encoder.encodeString.contramap { jt =>
    joinTypeMapping.find(_._1 == jt).map(_._2).getOrElse {
      throw new RuntimeException(s"Not implemented encoder for join type ${jt}")
    }
  }

  implicit val joinTypeDecoder: Decoder[JoinType] = Decoder.decodeString.emap { s =>
    joinTypeMapping.find(_._2 == s).map(x => Right(x._1)).getOrElse {
      Left(s"Decoder not found for ${s}")
    }
  }

  implicit def singleGeneratorProgramEncoder: ObjectEncoder[SingleTableGeneratorProgram] = {
    codec.contramapObject(x => x: TableGeneratorProgram)
  }

  implicit def singleGeneratorProgramDecoder: Decoder[SingleTableGeneratorProgram] = {
    codec.emap {
      case s: SingleTableGeneratorProgram => Right(s)
      case somethingElse                  => Left(s"Expected SingleTableGeneratorProgram, got ${somethingElse.getClass.getSimpleName}")
    }
  }

  implicit val codec: ObjectEncoder[TableGeneratorProgram] with Decoder[TableGeneratorProgram] =
    new DiscriminatorDependentCodec[TableGeneratorProgram]("type") {
      override val subTypes = Seq(
        makeSubType[UnionProgram]("union"),
        makeSubType[SelectProgram]("select", true),
        makeSubType[DataSource]("source"),
        makeSubType[JoinProgram]("join"),
        makeSubType[SplitProgram]("split")
      )
    }
}
