package ai.mantik.ds.sql

/** Transformation utilities for Expressions */
private[sql] object ExpressionTransformation {

  /** Fold expressions, from outer to inner, left to right */
  def foldTree[T](expression: Expression)(x0: T)(f: (T, Expression) => T): T = {
    val afterThis = f(x0, expression)
    expression.dependencies.foldLeft(afterThis) { case (c, n) =>
      foldTree(n)(c)(f)
    }
  }

  /** Maps a function on all expressions, deep to outer */
  def deepMap(expression: Expression)(f: Expression => Expression): Expression = {
    val updatedChildren = expression.dependencies.map(deepMap(_)(f))
    val updatedExpression = withDependencies(expression, updatedChildren)
    f(updatedExpression)
  }

  @throws[NoSuchElementException]("If arity fails")
  @throws[IllegalArgumentException]("If expression requirements are not matched")
  private def withDependencies(expression: Expression, dependencies: List[Expression]): Expression = {
    def ensureCondition(e: Expression): Condition = {
      e.asCondition.getOrElse {
        throw new IllegalArgumentException(s"Expected condition got ${e}")
      }
    }
    expression match {
      case l: LeafExpression =>
        l
      case b: BinaryOperationExpression =>
        b.copy(left = dependencies.head, right = dependencies.tail.head)
      case c: CastExpression =>
        c.copy(expression = dependencies.head)
      case c: Condition.IsNull =>
        c.copy(expression = dependencies.head)
      case c: Condition.Not =>
        c.copy(predicate = ensureCondition(dependencies.head))
      case c: Condition.And =>
        c.copy(left = ensureCondition(dependencies.head), right = ensureCondition(dependencies.tail.head))
      case c: Condition.WrappedExpression =>
        c.copy(expression = dependencies.head)
      case c: Condition.Equals =>
        c.copy(left = dependencies.head, right = dependencies.tail.head)
      case c: Condition.Or =>
        c.copy(left = ensureCondition(dependencies.head), right = ensureCondition(dependencies.tail.head))
      case c: ArrayGetExpression =>
        c.copy(array = ensureCondition(c.array), index = ensureCondition(c.index))
      case c: SizeExpression =>
        c.copy(expression = ensureCondition(c.expression))
      case c: StructAccessExpression =>
        c.copy(expression = ensureCondition(c.expression))

    }
  }
}
