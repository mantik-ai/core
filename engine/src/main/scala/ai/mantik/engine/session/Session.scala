package ai.mantik.engine.session

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import ai.mantik.planner.{ CoreComponents, MantikItem, PlanExecutor, Planner }
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

/**
 * A Single Mantik Engine Session.
 * @param id id of the session
 * @param components the core components as being viewed from the session.
 *        Note: it's possible that each session gets different instances to implement
 *        access controls.
 */
class Session(
    val id: String,
    val components: CoreComponents
) extends SessionBase {
  import Session.logger

  private var alive = true

  private val nextItemId = new AtomicInteger()
  private val items = new ConcurrentHashMap[String, MantikItem]()

  private def getNextItemId(): String = {
    val id = nextItemId.incrementAndGet()
    id.toString
  }

  def isAlive: Boolean = {
    this.synchronized {
      alive
    }
  }

  def quitSession(): Unit = {
    this.synchronized {
      alive = false
    }
    // More can be done in subclasses.
  }

  /** Add an item to the session, returns the id. */
  def addItem(item: MantikItem): String = {
    val itemId = getNextItemId()
    logger.debug(s"Session ${id} storing ${itemId}:${item.getClass.getSimpleName}")
    items.put(itemId, item)
    itemId
  }

  /** Returns a mantik item. */
  def getItem(id: String): Option[MantikItem] = {
    Option(items.get(id))
  }

  /** Returns a mantik item of given type or fail. */
  def getItemAs[T <: MantikItem: ClassTag](id: String): T = {
    getItem(id) match {
      case Some(item: T)   => item
      case Some(otherItem) => throw new ItemWrongTypeException(id, implicitly[ClassTag[T]].runtimeClass, otherItem)
      case None            => throw new ItemNotFoundException(id)
    }
  }

  def isEmpty: Boolean = items.isEmpty
}

object Session {
  private val logger = LoggerFactory.getLogger(classOf[Session])
}