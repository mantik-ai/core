package ai.mantik.ds.sql.run

import ai.mantik.ds.element.{ Element, NullElement, Primitive, TabularRow }
import ai.mantik.ds.sql.JoinType
import ai.mantik.ds.sql.run.SingleTableGeneratorProgramRunner.RowIterator

import scala.concurrent.{ Await, ExecutionContext, Future }
import com.google.common.collect.{ HashMultimap, Multimap }

import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._

class JoinRunner(joinProgram: JoinProgram) {

  private val filterProgram: Option[ProgramRunner] = joinProgram.filter.map(new ProgramRunner(_))

  type ElementSeq = IndexedSeq[Element]
  type GroupedData = HashMultimap[ElementSeq, ElementSeq]

  private implicit val ec: ExecutionContext = ExecutionContext.global

  val leftSizeWithoutGroup = joinProgram.left.result.columns.size - joinProgram.groupSize
  val rightSizeWithoutGroup = joinProgram.right.result.columns.size - joinProgram.groupSize

  def run(left: RowIterator, right: RowIterator): RowIterator = {
    val leftGroupedFuture = consumeAndGroup(left)
    val rightGroupedFuture = consumeAndGroup(right)
    val leftGrouped = Await.result(leftGroupedFuture, Duration.Inf)
    val rightGrouped = Await.result(rightGroupedFuture, Duration.Inf)

    val results = joinProgram.joinType match {
      case JoinType.Inner => executeJoin(leftGrouped, rightGrouped, false, false)
      case JoinType.Left  => executeJoin(leftGrouped, rightGrouped, true, false)
      case JoinType.Right => executeJoin(leftGrouped, rightGrouped, false, true)
      case JoinType.Outer => executeJoin(leftGrouped, rightGrouped, true, true)
    }

    val ordered = results.map(endSelect)
    ordered.map(TabularRow(_))
  }

  private def executeJoin(
    leftGrouped: GroupedData,
    rightGrouped: GroupedData,
    isLeftLike: Boolean,
    isRightLike: Boolean
  ): Iterator[ElementSeq] = {

    val rightEmpty = if (isLeftLike) {
      makeNulls(rightSizeWithoutGroup)
    } else {
      Vector.empty
    }

    val leftEmpty = if (isRightLike) {
      makeNulls(leftSizeWithoutGroup)
    } else {
      Vector.empty
    }

    val leftAndInner = iterateGrouped(leftGrouped) { (leftKey, leftFull) =>
      val inner = findMatching(leftKey, rightGrouped, right => leftFull ++ right, filterOk)
      val innerAndLeft = if (isLeftLike) {
        if (inner.isEmpty) {
          Iterator(leftFull ++ leftKey ++ rightEmpty)
        } else {
          inner
        }
      } else {
        inner
      }
      innerAndLeft
    }

    val rightValues = if (isRightLike) {
      iterateGrouped(rightGrouped) { (rightKey, rightFull) =>
        val matchingLeft = findMatching(rightKey, leftGrouped, left => left ++ rightFull, filterOk)
        // TODO: In effect we are double counting inner matches here
        if (matchingLeft.isEmpty) {
          Iterator(rightKey ++ leftEmpty ++ rightFull)
        } else {
          Iterator.empty
        }
      }
    } else {
      Iterator.empty
    }

    leftAndInner ++ rightValues
  }

  private def makeNulls(size: Int): ElementSeq = {
    (for (i <- 0 until size) yield NullElement).toVector
  }

  /**
   * Iteraete through grouped data
   * @param f function with key and full row for accessing sub elements mapping to results
   * @return iterate through results
   */
  private def iterateGrouped[T](grouped: GroupedData)(f: (ElementSeq, ElementSeq) => Iterator[T]): Iterator[T] = {
    grouped.entries().iterator().asScala.flatMap { entry =>
      val key = entry.getKey
      val value = entry.getValue
      val fullValue = key ++ value
      f(key, fullValue)
    }
  }

  private def findMatching(
    key: ElementSeq,
    grouped: GroupedData,
    builder: ElementSeq => ElementSeq,
    filter: ElementSeq => Boolean
  ): Iterator[ElementSeq] = {
    grouped.get(key).iterator().asScala.map { candidate =>
      builder(key ++ candidate)
    }.filter(filter)
  }

  private def filterOk(columns: IndexedSeq[Element]): Boolean = {
    filterProgram match {
      case Some(program) =>
        val result = program.run(columns)
        result.head.asInstanceOf[Primitive[Boolean]].x
      case None => true
    }
  }

  private def endSelect(columns: IndexedSeq[Element]): IndexedSeq[Element] = {
    joinProgram.selector.map(columns.apply)
  }

  private def consumeAndGroup(in: RowIterator): Future[GroupedData] = {
    Future {
      val result = HashMultimap.create[ElementSeq, ElementSeq]()
      in.foreach { row =>
        val group = row.columns.take(joinProgram.groupSize)
        val rest = row.columns.drop(joinProgram.groupSize)
        result.put(group, rest)
      }
      result
    }
  }
}
