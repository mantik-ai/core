package ai.mantik.ds.sql

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.element.Primitive
import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds._
import ai.mantik.ds.sql.builder.JoinBuilder

/** Formats Queries as SQL again. */
object SqlFormatter {

  def formatSql(query: Query): String = {
    query match {
      case AnonymousInput(_, slot) => "$" + s"$slot"
      case s: Select               => formatSelect(s)
      case u: Union                => formatUnion(u)
      case j: Join                 => formatJoin(j)
      case a: Alias                => formatAlias(a)
    }
  }

  def formatSelect(select: Select): String = {
    val formatter = new SqlFormatter(select.input.resultingQueryType)

    val projectionString = select.projections match {
      case None         => "*"
      case Some(values) => values.map(formatter.formatSelectProjection).mkString(", ")
    }
    val selectionString = select.selection match {
      case e if e.isEmpty      => ""
      case Vector(singleValue) => s"WHERE ${formatter.formatExpression(singleValue)}"
      case multiples =>
        val parts = multiples.map { x =>
          s"(${formatter.formatExpression(x)})"
        }
        s"WHERE ${parts.mkString("AND")}"
    }
    val fromQuery = select.input match {
      case a: AnonymousInput => formatSql(select.input)
      case other             => "(" + formatSql(other) + ")"
    }
    joinWithWhitespace("SELECT", projectionString, "FROM", fromQuery, selectionString)
  }

  def formatUnion(union: Union): String = {
    val left = formatSql(union.left)
    val right = formatSql(union.right)
    val all = formatOptional(union.all, "ALL")
    joinWithWhitespace(left, "UNION", all, right)
  }

  private def formatOptional(v: Boolean, s: String): String = {
    if (v) {
      s
    } else ""
  }

  private def joinWithWhitespace(s: String*): String = {
    s.filter(_.nonEmpty).mkString(" ")
  }

  def formatJoin(join: Join): String = {
    val left = formatSql(join.left)
    val right = formatSql(join.right)
    if (join.condition == JoinCondition.Cross) {
      return joinWithWhitespace(left, "CROSS", join.joinType.sqlName, right)
    }
    val condition = join.condition match {
      case JoinCondition.Cross => "" // should not come here but we want the check for exhaustive patterns
      case JoinCondition.On(condition) =>
        val formatter = new SqlFormatter(join.innerType)
        joinWithWhitespace("ON", formatter.formatExpression(condition))
      case JoinCondition.Using(columns) =>
        val formattedColumns = columns.map { column =>
          if (column.caseSensitive) {
            "\"" + column.name + "\""
          } else {
            column.name
          }
        }.mkString(", ")
        joinWithWhitespace("USING", formattedColumns)
    }
    joinWithWhitespace(left, join.joinType.sqlName, "JOIN", right, condition)
  }

  def formatAlias(alias: Alias): String = {
    joinWithWhitespace(formatSql(alias.query), "AS", alias.name)
  }
}

private[sql] class SqlFormatter(inputData: QueryTabularType) {

  def formatSelectProjection(projection: SelectProjection): String = {
    val exp = formatExpression(projection.expression)
    val as = "\"" + projection.columnName + "\""
    if (exp == as) {
      exp
    } else {
      s"(${exp}) AS ${as}"
    }
  }

  def formatExpression(expression: Expression): String = {
    expression match {
      case c: ConstantExpression => formatConstant(c)
      case c: ColumnExpression =>
        require(c.columnId >= 0 && c.columnId < inputData.columns.length, "Columns id out of range")
        val column = inputData.columns(c.columnId)
        formatColumnName(column)
      case c: CastExpression =>
        s"CAST (${formatExpression(c.expression)} AS ${formatCastToDataType(c.expression.dataType, c.dataType)})"
      case b: BinaryOperationExpression =>
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

  def formatColumnName(column: QueryColumn): String = {
    column.alias match {
      case Some(alias) => alias + "." + column.name
      case None        => "\"" + column.name + "\""
    }
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
