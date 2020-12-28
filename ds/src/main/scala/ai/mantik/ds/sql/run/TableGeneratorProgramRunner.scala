package ai.mantik.ds.sql.run

import ai.mantik.ds.TabularData
import ai.mantik.ds.element.{ TabularBundle, TabularRow }
import ai.mantik.ds.sql.run.TableGeneratorProgramRunner.{ QueryRunner, RowIterator, RowVector }
import cats.implicits._

import scala.collection.mutable

/** Executes TableGeneratorRunner programs  */
class TableGeneratorProgramRunner(tableGeneratorProgram: TableGeneratorProgram) {

  /** Id of the maximum input source */
  val maxInputSourceId: Int = maxInputSource(tableGeneratorProgram)

  /** Runs the query */
  private val queryRunner: QueryRunner = makeQueryRunner(tableGeneratorProgram)

  private def maxInputSource(program: TableGeneratorProgram): Int = {
    program match {
      case DataSource(id, _) => id
      case u: UnionProgram =>
        u.inputs.map(maxInputSource).max
      case s: SelectProgram =>
        s.input.map(maxInputSource).getOrElse(0)
      case j: JoinProgram =>
        maxInputSource(j.left).max(maxInputSource(j.right))
    }
  }

  private def makeQueryRunner(program: TableGeneratorProgram): QueryRunner = {
    program match {
      case DataSource(id, _) => inputs => inputs(id).iterator
      case s: SelectProgram =>
        makeSelectRunner(s)
      case u: UnionProgram =>
        makeUnionRunner(u)
      case j: JoinProgram =>
        makeJoinRunner(j)
    }
  }

  private def makeSelectRunner(select: SelectProgram): QueryRunner = {
    val subRunner = makeQueryRunner(select.input.getOrElse(DataSource(0, select.result)))
    val selectRunner = new SelectProgramRunner(select)
    subRunner.andThen(selectRunner.run)
  }

  private def makeUnionRunner(union: UnionProgram): QueryRunner = {
    val inputRunners = union.inputs.map(makeQueryRunner)
    inputs => {
      val inputIterators = inputRunners.map { runner =>
        runner(inputs)
      }
      if (inputIterators.isEmpty) {
        Iterator.empty
      } else {
        val concatenated = inputIterators.reduce(_ ++ _)
        if (union.all) {
          concatenated
        } else {
          withoutDuplicates(concatenated)
        }
      }
    }
  }

  private def makeJoinRunner(join: JoinProgram): QueryRunner = {
    val leftInputRunner = makeQueryRunner(join.left)
    val rightInputRunner = makeQueryRunner(join.right)
    val joinRunner = new JoinRunner(join)
    inputs => {
      val leftIterator = leftInputRunner(inputs)
      val rightIterator = rightInputRunner(inputs)
      joinRunner.run(leftIterator, rightIterator)
    }
  }

  private def withoutDuplicates(input: RowIterator): RowIterator = {
    new Iterator[TabularRow] {
      val already = mutable.HashSet[TabularRow]()
      var nextElement: Option[TabularRow] = findNext()

      override def hasNext: Boolean = {
        nextElement.isDefined
      }

      override def next(): TabularRow = {
        val result = nextElement.get
        nextElement = findNext()
        result
      }

      private def findNext(): Option[TabularRow] = {
        while (input.hasNext) {
          val x = input.next()
          if (already.add(x)) {
            return Some(x)
          }
        }
        None
      }
    }
  }

  @throws[IllegalArgumentException]("On illegal input size")
  def run(input: Vector[TabularBundle], resultingType: TabularData): TabularBundle = {
    require(input.size > maxInputSourceId, s"Expected at least ${maxInputSourceId + 1} elements")
    val rowsVectors = input.map(_.rows)
    val resultRows = queryRunner(rowsVectors).toVector
    TabularBundle(resultingType, resultRows)
  }
}

object TableGeneratorProgramRunner {
  type RowIterator = Iterator[TabularRow]
  type RowVector = Vector[TabularRow]
  type InputElements = Vector[RowVector]

  type QueryRunner = InputElements => RowIterator
}