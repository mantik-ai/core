package ai.mantik.ds.formats.natural

import ai.mantik.ds.Errors.{ EncodingException, FormatNotSupportedException }
import ai.mantik.ds.FundamentalType._
import ai.mantik.ds._
import ai.mantik.ds.element._
import akka.util.ByteString
import org.msgpack.core.{ MessageFormat, MessagePackException, MessagePacker, MessageUnpacker }

import scala.reflect.ClassTag

/** Contains Adapters from natural format to MessagePack and back. */
private[natural] object MessagePackAdapters {

  /** A MessagePack adapter with anonymous interface. */
  trait AnonymousMessagePackAdapter {
    /** Write an element, assuming it is of correct type. */
    def elementWriter(messagePacker: MessagePacker, value: Element)

    /** Read an element. */
    def read(messageUnpacker: MessageUnpacker): Element
  }

  /** Type class which adapts between MessagePack driver and data type incarnations. */
  trait MessagePackAdapter[T <: DataType] extends AnonymousMessagePackAdapter {
    type ElementType <: Element

    /** Write an element. */
    def write(messagePacker: MessagePacker, elementType: ElementType): Unit

    override def elementWriter(messagePacker: MessagePacker, value: Element): Unit = {
      write(messagePacker, value.asInstanceOf[ElementType])
    }

    /** Read an element. */
    def read(messageUnpacker: MessageUnpacker): ElementType
  }

  /**
   * A fundamental type, which has a scala representation.
   * @tparam T data type
   * @tparam ST  scala data type
   */
  class FundamentalMessagePackAdapter[T <: FundamentalType, ST: ClassTag](
      val writer: (MessagePacker, ST) => Unit,
      val reader: MessageUnpacker => ST
  ) extends MessagePackAdapter[T] {
    override final type ElementType = Primitive[ST]

    override final def write(messagePacker: MessagePacker, elementType: Primitive[ST]): Unit = {
      writer(messagePacker, elementType.x)
    }

    override final def read(messageUnpacker: MessageUnpacker): Primitive[ST] = {
      Primitive(reader(messageUnpacker))
    }

    /** Helper for allocating arrays, as the class Tag is required here. */
    private[natural] def allocateArray(length: Int): Array[ST] = new Array[ST](length)
  }

  type Aux[A0 <: DataType, B0] = MessagePackAdapter[A0] {
    type ElementType = B0
  }

  /** Generate an adapter for a fundamental type. */
  private def makeFundamentalAdapter[T <: FundamentalType, ST: ClassTag](
    writer: (MessagePacker, ST) => Unit,
    reader: (MessageUnpacker => ST)
  ) = new FundamentalMessagePackAdapter[T, ST](writer, reader)

  // Note for unsigned types
  // In Java World we only have signed.
  // and message pack doesn't export support for unsinged types https://github.com/msgpack/msgpack-java/issues/263
  // so we encode/decode by hand, see format: https://github.com/msgpack/msgpack/blob/master/spec.md#int-format-family

  implicit val boolAdapter = makeFundamentalAdapter[BoolType.type, Boolean](_.packBoolean(_), _.unpackBoolean())
  implicit val int32Adapter = makeFundamentalAdapter[Int32.type, Int](_.packInt(_), _.unpackInt())
  // implicit val uint32Adapter = makeFundamentalAdapter[Uint32.type, Int](_.packInt(_), _.unpackInt())

  implicit val uint32Adapter = makeFundamentalAdapter[Uint32.type, Int](
    { (packer, value) =>
      packer.packLong(Integer.toUnsignedLong(value))
    },
    { unpacker =>
      unpacker.getNextFormat match {
        case MessageFormat.UINT32 =>
          val data = unpacker.unpackLong()
          data.toInt
        case _ =>
          unpacker.unpackInt()
      }
    }
  )

  implicit val int8Adapter = makeFundamentalAdapter[Int8.type, Byte](_.packByte(_), _.unpackByte())

  implicit val uint8Adapter = makeFundamentalAdapter[Uint8.type, Byte](
    { (packer, byte) =>
      if (byte < 0) { // value is 128 .. 255
        val unsignedShort: Short = (byte.toShort & 0xff.toShort).toShort
        packer.packShort(unsignedShort)
      } else {
        packer.packByte(byte)
      }
    },
    { unpacker =>
      unpacker.getNextFormat match {
        case MessageFormat.UINT8 =>
          val short = unpacker.unpackShort()
          short.toByte
        case _ =>
          unpacker.unpackByte()
      }
    }
  )
  implicit val int64Adapter = makeFundamentalAdapter[Int64.type, Long](_.packLong(_), _.unpackLong())
  // implicit val uint64Adapter = makeFundamentalAdapter[Uint64.type, Long](_.packLong(_), _.unpackLong())

  implicit val uint64Adapter = makeFundamentalAdapter[Uint64.type, Long](
    { (packer, long) =>
      if (long < 0) { // value is 128 .. 255
        // unsigned long
        val highBytes = long >> 32 & 0xffffffffL
        val lowBytes = long & 0xffffffffL
        val bigInt = (BigInt(highBytes) << 32) + BigInt(lowBytes)
        packer.packBigInteger(bigInt.bigInteger)
      } else {
        packer.packLong(long)
      }
    },
    { unpacker =>
      unpacker.getNextFormat match {
        case MessageFormat.UINT64 =>
          val bigInt = BigInt(unpacker.unpackBigInteger())
          val result = bigInt.toLong // truncation is applied automatically
          result
        case _ =>
          unpacker.unpackLong()
      }
    }
  )

  implicit val stringAdapter = makeFundamentalAdapter[StringType.type, String](_.packString(_), _.unpackString())
  implicit val float32Adapter = makeFundamentalAdapter[Float32.type, Float](_.packFloat(_), _.unpackFloat())
  implicit val float64Adapter = makeFundamentalAdapter[Float64.type, Double](_.packDouble(_), _.unpackDouble())

  implicit val voidAdapter = makeFundamentalAdapter[VoidType.type, Unit](
    { (packer, _) => packer.packNil() },
    { unpacker =>
      unpacker.unpackNil()
    }
  )

  /** Adapter for images. */
  implicit object imageAdapter extends MessagePackAdapter[Image] {

    override type ElementType = ImageElement

    override def write(messagePacker: MessagePacker, scalaType: ImageElement): Unit = {
      messagePacker.packBinaryHeader(scalaType.bytes.length)
      messagePacker.writePayload(scalaType.bytes.toArray)
    }

    override def read(messageUnpacker: MessageUnpacker): ImageElement = {
      val length = messageUnpacker.unpackBinaryHeader()
      val buffer = new Array[Byte](length)
      messageUnpacker.readPayload(buffer)
      ImageElement(ByteString.fromArrayUnsafe(buffer))
    }
  }

  /** Adapter for embedded tables. */
  def embeddedTabularAdapter(tabularData: TabularData) = new MessagePackAdapter[TabularData] {
    override type ElementType = EmbeddedTabularElement

    private val context = new TabularContext(tabularData)

    override def write(messagePacker: MessagePacker, elementType: EmbeddedTabularElement): Unit = {
      messagePacker.packArrayHeader(elementType.rows.length)
      elementType.rows.foreach { row =>
        context.write(messagePacker, row)
      }
    }

    override def read(messageUnpacker: MessageUnpacker): EmbeddedTabularElement = {
      val elementCount = messageUnpacker.unpackArrayHeader()
      val buffer = IndexedSeq.newBuilder[TabularRow]
      buffer.sizeHint(elementCount)
      for (i <- 0 until elementCount) {
        buffer += context.read(messageUnpacker)
      }
      EmbeddedTabularElement(buffer.result())
    }
  }

  /** Adapter for Tensors. */
  def tensorAdapter[ST](tensor: Tensor) = new MessagePackAdapter[Tensor] {
    override type ElementType = TensorElement[ST]

    private val underlyingAdapter = lookupAdapter(tensor.componentType)
      .asInstanceOf[FundamentalMessagePackAdapter[_, ST]]

    override def write(messagePacker: MessagePacker, elementType: TensorElement[ST]): Unit = {
      require(elementType.elements.length == tensor.packedElementCount, s"Tensor element count mismatch, expected: ${tensor.packedElementCount}, got: ${elementType.elements.length}")
      messagePacker.packArrayHeader(elementType.elements.length)
      elementType.elements.foreach { element =>
        underlyingAdapter.writer(messagePacker, element)
      }
    }

    override def read(messageUnpacker: MessageUnpacker): TensorElement[ST] = {
      val arrayLength = messageUnpacker.unpackArrayHeader()
      val array = underlyingAdapter.allocateArray(arrayLength)
      for (i <- 0 until arrayLength) {
        array(i) = underlyingAdapter.reader(messageUnpacker)
      }
      TensorElement(array)
    }
  }

  /** Writes/Reads root elements to MessagePack. */
  trait RootElementContext {
    def write(messagePacker: MessagePacker, rootElement: RootElement): Unit

    @throws[EncodingException]
    def read(messageUnpacker: MessageUnpacker): RootElement
  }

  /** Creates the root element context. */
  def createRootElementContext(dataType: DataType): RootElementContext = {
    dataType match {
      case t: TabularData => new TabularContext(t)
      case other          => new SingleElementContext(other)
    }
  }

  /** Reads/Writes tabular Rows. */
  private class TabularContext(tabularData: TabularData) extends RootElementContext {
    val subAdapters = tabularData.columns.values.map(lookupAdapter).toIndexedSeq

    override def write(messagePacker: MessagePacker, rootElement: RootElement): Unit = {
      val tabularRow = rootElement.asInstanceOf[TabularRow]
      require(tabularRow.columns.size == subAdapters.length)
      messagePacker.packArrayHeader(tabularRow.columns.size)
      tabularRow.columns.zip(subAdapters).foreach {
        case (element, adapter) =>
          adapter.elementWriter(messagePacker, element)
      }
    }

    override def read(messageUnpacker: MessageUnpacker): TabularRow = {
      try {
        val length = messageUnpacker.unpackArrayHeader()
        require(length == subAdapters.length)
        val builder = IndexedSeq.newBuilder[Element]
        builder.sizeHint(length)
        subAdapters.foreach { adapter =>
          builder += adapter.read(messageUnpacker)
        }
        TabularRow(
          builder.result()
        )
      } catch {
        case e: MessagePackException =>
          throw new EncodingException("Error on decoding", e)
      }
    }
  }

  /** Reads/Writes single elements. */
  private class SingleElementContext(dataType: DataType) extends RootElementContext {
    val subAdapter = lookupAdapter(dataType)

    override def write(messagePacker: MessagePacker, rootElement: RootElement): Unit = {
      val singleElement = rootElement.asInstanceOf[SingleElement]
      subAdapter.elementWriter(messagePacker, singleElement.element)
    }

    override def read(messageUnpacker: MessageUnpacker): RootElement = {
      try {
        SingleElement(subAdapter.read(messageUnpacker))
      } catch {
        case e: MessagePackException =>
          throw new EncodingException("Error on decoding", e)
      }
    }
  }

  /** Lookup adapters for a specific type. */
  def lookupAdapter(dt: DataType): AnonymousMessagePackAdapter = {
    val result = dt match {
      case BoolType       => boolAdapter
      case Int8           => int8Adapter
      case Uint8          => uint8Adapter
      case Int32          => int32Adapter
      case Uint32         => uint32Adapter
      case Int64          => int64Adapter
      case Uint64         => uint64Adapter
      case StringType     => stringAdapter
      case Float32        => float32Adapter
      case Float64        => float64Adapter
      case VoidType       => voidAdapter
      case _: Image       => imageAdapter
      case t: TabularData => embeddedTabularAdapter(t)
      case t: Tensor      => tensorAdapter(t)
      // Let the compiler warn if we have something incomplete here
    }
    result
  }
}
