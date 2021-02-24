package ai.mantik.ds.sql.run

import ai.mantik.ds.sql._
import ai.mantik.ds.sql.run.JoinConditionAnalyzer.Analysis
import ai.mantik.ds.{ DataType, Nullable, TabularData }

import scala.collection.BitSet

private[sql] object JoinCompiler {
  def compile(join: Join): Either[String, JoinProgram] = {
    new JoinCompiler(join).result
  }
}

private[sql] class JoinCompiler(join: Join) {

  /*
  Extension Points
    - Optimizing data flows to avoid duplicates
   */
  type Result[T] = Either[String, T]

  /** The compiled join program */
  lazy val result: Either[String, JoinProgram] = {
    for {
      analysis <- JoinConditionAnalyzer.analyze(join)
      sources <- generateSources(analysis)
      leftSource = sources._1
      rightSource = sources._2
      filter <- analysis.filterProgram
      selector <- exportSelector(analysis)
    } yield JoinProgram(
      left = leftSource,
      right = rightSource,
      groupSize = analysis.groupSize,
      joinType = join.joinType,
      filter = filter,
      selector = selector,
      result = join.resultingTabularType
    )
  }

  private def generateSources(analysis: JoinConditionAnalyzer.Analysis): Either[String, (SingleTableGeneratorProgram, SingleTableGeneratorProgram)] = {
    for {
      left <- Compiler.compile(join.left)
      right <- Compiler.compile(join.right)
      leftGrouper <- analysis.leftGrouper
      rightGrouper <- analysis.rightGrouper
      elementProjectors <- elementProjector()
      leftProjector = elementProjectors._1
      rightProjector = elementProjectors._2
      leftFullSource = buildInputSource(left, analysis.groupTypes, leftGrouper, leftProjector._1, leftProjector._2)
      rightFullSource = buildInputSource(right, analysis.groupTypes, rightGrouper, rightProjector._1, rightProjector._2)
    } yield {
      (leftFullSource, rightFullSource)
    }
  }

  private def buildInputSource(
    input: SingleTableGeneratorProgram,
    groupingDataTypes: Vector[DataType],
    groupingProgram: Program,
    elementTypes: Vector[DataType],
    elementProgram: Program
  ): SingleTableGeneratorProgram = {
    val fullDataType = groupingDataTypes ++ elementTypes
    val asTabular = TabularData(fullDataType.zipWithIndex.map {
      case (dt, i) =>
        s"_${i}" -> dt
    }: _*)
    val fullProgram = groupingProgram ++ elementProgram
    SelectProgram(
      Some(input),
      selector = None,
      projector = Some(fullProgram),
      result = asTabular
    )
  }

  /**
   * Projets the elements into what is important for the program
   * (Note: here is a lot of air for optimizing)
   */
  private def elementProjector(): Either[String, ((Vector[DataType], Program), (Vector[DataType], Program))] = {
    join.joinType match {
      case JoinType.Inner => Right((makePureReturningProgram(join.left), makePureReturningProgram(join.right)))
      case JoinType.Left  => Right((makePureReturningProgram(join.left), makeNullableConversionProgram(join.right)))
      case JoinType.Right => Right((makeNullableConversionProgram(join.left), makePureReturningProgram(join.right)))
      case JoinType.Outer => Right((makeNullableConversionProgram(join.left), makeNullableConversionProgram(join.right)))
    }
  }

  /** Returns a program which just returns all columns */
  private def makePureReturningProgram(query: Query): (Vector[DataType], Program) = {
    val ops = (for {
      i <- 0 until query.resultingQueryType.size
    } yield {
      OpCode.Get(i)
    }).toVector
    query.resultingQueryType.columns.map(_.dataType) -> Program.fromOps(ops)
  }

  /** Returns a program which converts all columns into nullables. */
  private def makeNullableConversionProgram(query: Query): (Vector[DataType], Program) = {
    val parts = query.resultingQueryType.columns.zipWithIndex.map {
      case (c, id) =>
        c.dataType match {
          case Nullable(_) => c.dataType -> Vector(OpCode.Get(id))
          case other       => Nullable(c.dataType) -> Vector(OpCode.Get(id), OpCode.Cast(c.dataType, Nullable(c.dataType)))
        }
    }
    val dataTypes = parts.map(_._1)
    val ops = parts.flatMap(_._2)
    (dataTypes, Program.fromOps(ops))
  }

  private def exportSelector(analysis: Analysis): Either[String, Vector[Int]] = {
    // Right now we have [left group][left value][right group][right value] as data input
    // For OUTER joins we need to prefer groups. This looks a bit hacky.
    // In future we should mix groups and values as this would avoid a lot of unnecessary data

    def resolveInnerId(id: Int): Int = {
      if (id < join.left.resultingQueryType.size) {
        // Left side

        // Prefer groups, HACKY
        val comparisonId = analysis.comparisons.indexWhere { c =>
          c.columnIds.map(_._1).contains(id)
        }
        comparisonId match {
          case -1 => id + analysis.groupSize // left value
          case n  => n // left group
        }
      } else {
        // Right side
        val rightId = id - join.left.resultingQueryType.size
        val comparisonId = analysis.comparisons.indexWhere { c =>
          c.columnIds.map(_._2).contains(rightId)
        }
        comparisonId match {
          case -1 => id + 2 * analysis.groupSize // right value
          case n  => join.left.resultingQueryType.size + analysis.groupSize + n // right group
        }
      }
    }

    def resolveOuterId(id: Int): Int = {
      val column = join.resultingQueryType.columns(id)
      join.innerType.columns.indexOf(column) match {
        case -1 => throw new IllegalStateException(s"Could not map outer id to inner")
        case n  => resolveInnerId(n)
      }
    }

    val mapping = join.resultingQueryType.columns.indices.map { resolveOuterId }
    Right(mapping.toVector)
  }

}
