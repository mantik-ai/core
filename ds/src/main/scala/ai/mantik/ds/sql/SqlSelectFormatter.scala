package ai.mantik.ds.sql

import ai.mantik.ds.element.Primitive
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds._

/** Formats Expressions as SQL again. */
object SqlSelectFormatter {

  def formatSql(select: Select): String = {
    val formatter = new SqlSelectFormatter(select.inputType)

    val projectionString = select.projections match {
      case None         => "*"
      case Some(values) => values.map(formatter.formatSelectProjection).mkString(", ")
    }
    val selectionString = select.selection match {
      case Nil               => ""
      case List(singleValue) => s"WHERE ${formatter.formatExpression(singleValue)}"
      case multiples =>
        val parts = multiples.map { x =>
          s"(${formatter.formatExpression(x)})"
        }
        s"WHERE ${parts.mkString("AND")}"
    }
    s"SELECT ${projectionString} ${selectionString}".trim
  }

}

private class SqlSelectFormatter(inputData: TabularData) {

  private val columnNames = inputData.columns.keys.toVector

  def formatSelectProjection(projection: SelectProjection): String = {
    val exp = formatExpression(projection.expression)
    val as = formatColumnName(projection.columnName)
    if (exp == as) {
      return exp
    } else {
      s"(${exp}) AS ${as}"
    }
  }

  def formatExpression(expression: Expression): String = {
    expression match {
      case c: ConstantExpression => formatConstant(c)
      case c: ColumnExpression =>
        require(c.columnId >= 0 && c.columnId < columnNames.length, "Columns id out of range")
        val columnName = columnNames(c.columnId)
        formatColumnName(columnName)
      case c: CastExpression =>
        s"CAST (${formatExpression(c.expression)} AS ${formatCastToDataType(c.expression.dataType, c.dataType)})"
      case b: BinaryExpression =>
        formatBinary(b.left, b.right, formatBinaryOperationSign(b.op))
      case e: Condition.Equals =>
        formatBinary(e.left, e.right, "=")
      case Condition.Not(Condition.IsNull(underlying)) =>
        s"${formatExpression(underlying)} IS NOT NULL"
      case n: Condition.Not =>
        s"NOT(${formatExpression(n.predicate)})"
      case a: Condition.And =>
        formatBinary(a.left, a.right, "AND")
      case o: Condition.Or =>
        formatBinary(o.left, o.right, "OR")
      case isNull: Condition.IsNull =>
        s"${formatExpression(isNull.expression)} IS NULL"
      case w: Condition.WrappedExpression =>
        formatExpression(w.expression)
    }
  }

  def formatColumnName(name: String): String = {
    require(!name.contains("\""))
    "\"" + name + "\""
  }

  def formatBinary(left: Expression, right: Expression, sign: String): String = {
    s"(${formatExpression(left)} ${sign} ${formatExpression(right)})"
  }

  def formatCastToDataType(from: DataType, dataType: DataType): String = {

    def extractSingleUnderlying(dataType: DataType): Option[FundamentalType] = {
      dataType match {
        case f: FundamentalType                 => Some(f)
        case i: Image if i.components.size == 1 => Some(i.components.head._2.componentType)
        case t: Tensor                          => Some(t.componentType)
        case _                                  => None
      }
    }

    def maybeUnderlyingCast(): String = {
      val fromUnderlying = extractSingleUnderlying(from)
      val toUnderlying = extractSingleUnderlying(dataType)
      (fromUnderlying, toUnderlying) match {
        case (Some(x), Some(y)) if x == y => "" // no extra cast needed
        case (Some(_), Some(y))           => s" OF ${y.name}"
        case _ =>
          throw new IllegalStateException(s"Cannot serialize cast from $from to $dataType")
      }
    }
    dataType match {
      case f: FundamentalType => f.name
      case i: Image =>
        val colorString = i.components.headOption match {
          case Some((c, _)) if c != ImageChannel.Black => s" IN ${c.name}"
          case _                                       => ""
        }
        "image" + maybeUnderlyingCast() + colorString
      case t: Tensor =>
        "tensor" + maybeUnderlyingCast()
      case Nullable(underlying) =>
        formatCastToDataType(from, underlying) + " NULLABLE"
      case _ => throw new IllegalArgumentException(s"Unsupported data type ${dataType}")
    }
  }

  def formatBinaryOperationSign(op: BinaryOperation): String = {
    op match {
      case BinaryOperation.Mul => "*"
      case BinaryOperation.Div => "/"
      case BinaryOperation.Sub => "-"
      case BinaryOperation.Add => "+"
    }
  }

  def formatConstant(constantExpression: ConstantExpression): String = {
    val value = constantExpression.value
    value.model match {
      case FundamentalType.StringType => {
        val stringValue = value.element.asInstanceOf[Primitive[String]].x
        "'" + stringValue.replace("'", "''") + "'"
      }
      case FundamentalType.VoidType =>
        "void"
      case other =>
        // TODO: Proof that the JSON value is valid for this constant.
        JsonFormat.serializeBundleValue(value).toString()
    }
  }
}
