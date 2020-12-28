package ai.mantik.ds.sql.builder

import ai.mantik.ds.sql.parser.AST
import ai.mantik.ds.sql._
import ai.mantik.ds.{ DataType, TabularData }
import cats.implicits._

private[sql] object JoinBuilder {

  def buildJoinFromParsed(parsed: AST.JoinNode)(implicit sqlContext: SqlContext): Either[String, Join] = {
    for {
      leftQuery <- QueryBuilder.buildQueryFromParsed(parsed.left)
      rightQuery <- QueryBuilder.buildQueryFromParsed(parsed.right)
      joinType = convertJoinType(parsed.joinType)
      innerModel = innerTabularData(leftQuery, rightQuery, joinType)
      condition <- buildJoinCondition(leftQuery, rightQuery, joinType, innerModel, parsed.condition)
    } yield Join(leftQuery, rightQuery, joinType, condition)
  }

  private def convertJoinType(joinType: AST.JoinType): JoinType = {
    joinType match {
      case AST.JoinType.Inner => JoinType.Inner
      case AST.JoinType.Left  => JoinType.Left
      case AST.JoinType.Right => JoinType.Right
      case AST.JoinType.Outer => JoinType.Outer
    }
  }

  /** Determine column names and data types as being used during JOIN Expressions */
  def innerTabularData(left: Query, right: Query, joinType: JoinType): QueryTabularType = {
    val leftMaybeNullable = if (joinType == JoinType.Right || joinType == JoinType.Outer) {
      left.resultingQueryType.makeNullable
    } else {
      left.resultingQueryType
    }

    val rightMaybeNullable = if (joinType == JoinType.Left || joinType == JoinType.Outer) {
      right.resultingQueryType.makeNullable
    } else {
      right.resultingQueryType
    }
    val combined = leftMaybeNullable ++ rightMaybeNullable
    val shadowed = if (joinType == JoinType.Right) {
      combined.shadow(false)
    } else {
      combined.shadow(true)
    }
    shadowed
  }

  private def buildJoinCondition(left: Query, right: Query, joinType: JoinType, innerModel: QueryTabularType, condition: AST.JoinCondition): Either[String, JoinCondition] = {
    condition match {
      case AST.JoinCondition.Cross        => Right(JoinCondition.Cross)
      case on: AST.JoinCondition.On       => buildOnCondition(innerModel, on)
      case using: AST.JoinCondition.Using => buildUsingCondition(left, right, joinType, using)
    }

  }

  private def buildOnCondition(innerModel: QueryTabularType, condition: AST.JoinCondition.On): Either[String, JoinCondition.On] = {
    for {
      expression <- ExpressionBuilder.convertExpression(innerModel, condition.expression)
      asCondition <- extractCondition(expression, innerModel)
    } yield JoinCondition.On(asCondition)
  }

  private def buildUsingCondition(left: Query, right: Query, joinType: JoinType, condition: AST.JoinCondition.Using): Either[String, JoinCondition.Using] = {
    condition.columns.map { identifierNode =>
      for {
        leftLookup <- ExpressionBuilder.findColumnByIdentifier(left.resultingQueryType, identifierNode)
        leftId = leftLookup._1
        leftDataType = leftLookup._2
        rightLookup <- ExpressionBuilder.findColumnByIdentifier(right.resultingQueryType, identifierNode)
        rightId = rightLookup._1
        rightDataType = rightLookup._2
        comparisonType <- CastBuilder.comparisonType(leftDataType, rightDataType)
      } yield {
        val dropId = if (joinType == JoinType.Right) {
          leftId
        } else {
          rightId + left.resultingQueryType.columns.size
        }
        JoinCondition.UsingColumn(
          identifierNode.name,
          caseSensitive = !identifierNode.ignoreCase,
          leftId = leftId,
          rightId = rightId,
          dropId = dropId,
          dataType = comparisonType
        )
      }
    }.sequence.map(JoinCondition.Using.apply)
  }

  // Do not delete code yet, its possible that we need some of the Algorithms for the Compiler

  /*
  /** Build comparison expression based upon column names in USING-Clause */
  private def buildUsingComparisonExpressions(left: Query, right: Query, using: Option[List[AST.IdentifierNode]]): Either[String, List[(Expression, Expression)]] = {
    using.getOrElse(Nil).map { identifierNode =>
      for {
        leftExp <- ExpressionBuilder.buildColumnExpressionByIdentifier(left.resultingType, identifierNode)
        rightExp <- ExpressionBuilder.buildColumnExpressionByIdentifier(right.resultingType, identifierNode)
        commonType <- CastBuilder.comparisonType(leftExp, rightExp)
        leftCasted <- CastBuilder.wrapType(leftExp, commonType)
        rightCasted <- CastBuilder.wrapType(rightExp, commonType)
      } yield (leftCasted, rightCasted)
    }.sequence
  }

  /** Build the list of columns (and their counterpart drop) for the using field. */
  private def buildUsing(
    left: Query,
    right: Query,
    using: Option[List[AST.IdentifierNode]],
    joinType: JoinType,
    innerModel: TabularData
  ): Either[String, List[(String, String)]] = {
    val query = if (joinType == JoinType.Right) {
      left
    } else {
      right
    }
    using.getOrElse(Nil).map { identifierNode =>
      ExpressionBuilder.findColumnByIdentifier(query.resultingType, identifierNode).map {
        case (id, _) =>
          val idToDrop = if (joinType == JoinType.Right) {
            // drop on the left side
            id
          } else {
            // drop on the right side
            id + left.resultingType.columns.size
          }
          val usingName = if (identifierNode.ignoreCase) {
            identifierNode.name
          } else {
            "\"" + identifierNode.name + "\""
          }
          val usingDrop = innerModel.columnByIndex(idToDrop).get._1
          (usingName, usingDrop)
      }
    }.sequence
  }

  /** Translate ON Expressions into selectors and filters. */
  private def buildOnComparisonSelectorAndFilter(left: Query, right: Query, on: Option[AST.ExpressionNode], innerModel: TabularData): Either[String, (List[(Expression, Expression)], List[Condition])] = {
    expressionNormalForm(on, innerModel).map { conditions =>
      // Split conditions into ones which we can use as left/right selector and those who have to applied later
      val splitted: List[Either[(Expression, Expression), Condition]] = conditions.map { condition =>
        extractLeftRightCondition(left, right, innerModel, condition) match {
          case Some((left, right)) => Left((left -> right))
          case None                => Right(condition)
        }
      }

      val comparisons = splitted.collect {
        case Left((left, right)) => (left, right)
      }

      val filter = splitted.collect {
        case Right(filterCondition) => filterCondition
      }

      (comparisons, filter)
    }
  }

  /**
   * Extract comparisonExpressions from a condition, if possible.
   * @return comparison expression for the left and for the right side
   */
  private def extractLeftRightCondition(left: Query, right: Query, innerModel: TabularData, condition: Condition): Option[(Expression, Expression)] = {
    /** Returns the dependencies of an expression */
    def dependencies(expression: Expression): BitSet = {
      ExpressionTransformation.foldTree(expression)(BitSet.empty) {
        case (current, ColumnExpression(id, _)) => current + id
        case (current, _)                       => current
      }
    }

    val leftSize = left.resultingType.columns.size

    def isFromLeftQuery(dependencyId: Int): Boolean = {
      dependencyId < leftSize
    }

    /**
     * Translates an expression so that it's looking for input of the right side only
     * (columnIds are shifted)
     */
    def translateToRightPerspective(expression: Expression): Expression = {
      ExpressionTransformation.deepMap(expression) {
        case c: ColumnExpression => c.copy(columnId = c.columnId - leftSize)
        case otherwise           => otherwise
      }
    }

    condition match {
      case Condition.Equals(leftEq, rightEq) =>
        // Note: leftEq and rightEq mean the sides of the comparison
        // if they completely match leftQuery and rightQuery will be checked now

        val leftEqDependencies = dependencies(leftEq)
        val rightEqDependencies = dependencies(rightEq)

        val leftEqIsFromLeftQueryOnly = leftEqDependencies.forall(isFromLeftQuery)
        val leftEqIsFromRightQueryOnly = leftEqDependencies.forall(x => !isFromLeftQuery(x))
        val rightEqIsFromLeftQueryOnly = rightEqDependencies.forall(isFromLeftQuery)
        val rightEqIsFromRightQueryOnly = rightEqDependencies.forall(x => !isFromLeftQuery(x))

        if (leftEqIsFromLeftQueryOnly && rightEqIsFromRightQueryOnly) {
          Some(leftEq -> translateToRightPerspective(rightEq))
        } else if (leftEqIsFromRightQueryOnly && rightEqIsFromLeftQueryOnly) {
          Some(rightEq -> translateToRightPerspective(leftEq))
        } else {
          None
        }
      case _ =>
        None
    }
  }

  /** Convert On-Clause into AND-Concatenated expressions. */
  private def expressionNormalForm(on: Option[AST.ExpressionNode], innerModel: TabularData): Either[String, List[Condition]] = {
    val input = on.getOrElse(AST.BoolNode(true))
    for {
      expression <- ExpressionBuilder.convertExpression(innerModel, input)
      asCondition <- extractCondition(expression, innerModel)
      normalForm <- buildNormalForm(asCondition)
    } yield normalForm
  }
   */

  private def extractCondition(expression: Expression, innerModel: QueryTabularType): Either[String, Condition] = {
    expression.asCondition match {
      case Some(c) => Right(c)
      case None =>
        val formatted = new SqlFormatter(innerModel).formatExpression(expression)
        Left(s"Expected condition, got ${formatted}")
    }
  }

  /*
  private def buildNormalForm(expression: Condition): Either[String, List[Condition]] = {
    // Note: here is also room for optimizations
    expression match {
      case Condition.And(left, right) =>
        for {
          leftNf <- buildNormalForm(left)
          rightNf <- buildNormalForm(right)
        } yield (leftNf ++ rightNf)
      case Condition.WrappedExpression(ConstantExpression(SingleElementBundle(FundamentalType.BoolType, Primitive(x)))) if x == true =>
        Right(Nil)
      case somethingElse =>
        Right(List(somethingElse))
    }
  }
   */
}
