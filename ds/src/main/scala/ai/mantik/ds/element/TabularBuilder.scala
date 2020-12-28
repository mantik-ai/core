package ai.mantik.ds.element

import ai.mantik.ds.{ FundamentalType, Image, Nullable, TabularData, Tensor }

/** Builder for tabular data */
class TabularBuilder(tabularData: TabularData) {
  private val rowBuilder = Vector.newBuilder[TabularRow]

  /** Add a row (just use the pure Scala Types, no Primitives or similar. */
  def row(values: Any*): TabularBuilder = {
    addCheckedRow(values)
    this
  }

  def result: TabularBundle = TabularBundle(
    tabularData, rowBuilder.result()
  )

  private def addCheckedRow(values: Seq[Any]): Unit = {
    require(values.length == tabularData.columns.size)
    val converted = values.zip(tabularData.columns).map {
      case (value, (columnName, pt: FundamentalType)) =>
        val encoder = PrimitiveEncoder.lookup(pt)
        require(encoder.convert.isDefinedAt(value), s"Value  ${value} of class ${value.getClass} must fit to ${pt}")
        encoder.wrap(encoder.convert(value))
      case (value, (columnName, i: Image)) =>
        require(value.isInstanceOf[ImageElement])
        value.asInstanceOf[ImageElement]
      case (value: EmbeddedTabularElement, (columnName, d: TabularData)) =>
        value
      case (value, (columnName, i: Tensor)) =>
        require(value.isInstanceOf[TensorElement[_]])
        value.asInstanceOf[Element]
      case (value, (columnName, n: Nullable)) =>
        value match {
          case NullElement    => NullElement
          case None           => NullElement
          case s: SomeElement => s
          case v if n.underlying.isInstanceOf[FundamentalType] =>
            val encoder = PrimitiveEncoder.lookup(n.underlying.asInstanceOf[FundamentalType])
            require(encoder.convert.isDefinedAt(v), s"Value ${value} of class ${value.getClass} must fit into ${n}")
            SomeElement(encoder.wrap(encoder.convert(v)))
          case unsupported =>
            throw new IllegalArgumentException(s"Unsupported value ${unsupported} for ${n}")
        }
      case (other, (columnName, dataType)) =>
        throw new IllegalArgumentException(s"Could not encode ${other} as ${dataType}")
    }
    rowBuilder += TabularRow(converted.toVector)
  }

}
