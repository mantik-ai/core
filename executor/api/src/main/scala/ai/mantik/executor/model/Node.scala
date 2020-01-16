package ai.mantik.executor.model

import ai.mantik.componently.utils.Renderable
import ai.mantik.executor.model.docker.Container
import io.circe.{ Decoder, Encoder, ObjectEncoder }
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto

/** Something which provides the Node functionality, either a container or an existing service. */
@JsonCodec
sealed trait NodeService

object NodeService {
  implicit val renderable = Renderable.makeRenderable[NodeService] {
    case c: ContainerService => Renderable.buildRenderTree(c)(ContainerService.renderable)
    case e: ExistingService  => Renderable.Leaf(s"Existing: ${e.url}")
  }
}

/**
 * Defines a service, which is using contains for providing it.
 *
 * @param main the main container, whose port will be accessed
 * @param dataProvider describes initialisation of the side car.
 * @param port the port where resources can be found from the main container.
 * @param ioAffine flag, that the service is mainly doing IO, which prefers placement near sources/sinks.
 */
@JsonCodec
case class ContainerService(
    main: Container,
    dataProvider: Option[DataProvider] = None,
    port: Int = ExecutorModelDefaults.Port,
    ioAffine: Boolean = false
) extends NodeService

object ContainerService {
  implicit val renderable = Renderable.makeRenderable[ContainerService] { cs =>
    Renderable.keyValueList(
      "ContainerService",
      "main" -> cs.main,
      "dataProvider" -> cs.dataProvider,
      "port" -> cs.port,
      "ioAffine" -> cs.ioAffine
    )
  }
}

/**
 * Describes how to initialize a container
 * @param url Zip file URL which is unpacked to /data
 * @param mantikHeader MantikHeader content which is put to /data/MantikHeader
 */
@JsonCodec
case class DataProvider(
    url: Option[String] = None,
    mantikHeader: Option[String] = None
)

object DataProvider {
  implicit val renderable: Renderable[DataProvider] = Renderable.makeRenderable[DataProvider] { d =>
    Renderable.keyValueList(
      "DataProvider",
      "url" -> d.url,
      "mantikHeader" -> d.mantikHeader.map(Renderable.renderPotentialJson)
    )
  }
}

/**
 * Defines a reference to an existing service.
 */
case class ExistingService(url: String) extends NodeService

/** The type of a Resource. */
@JsonCodec
sealed trait ResourceType

object ResourceType {

  /** The resource can be read through GET. */
  case object Source extends ResourceType

  /**
   * The resource can be written to via POST.
   * Sinks are generally stateful.
   */
  case object Sink extends ResourceType

  /**
   * The resource transforms data via POST.
   * Note: Transformations should be stateless.
   */
  case object Transformer extends ResourceType
}

/**
 * Describes a Node in the runtime model.
 *
 * @param service Describes what is needed to access resources on this node.
 * @param resources Describes the resources provided by this node in this graph.
 *
 * @tparam T The node Service type (usually [[NodeService]])
 */
case class Node[+T](
    service: T,
    resources: Map[String, NodeResource]
)

/** A Node Resource. */
@JsonCodec
case class NodeResource(
    resourceType: ResourceType,
    contentType: Option[String] = None
)

object Node {

  implicit def encoder[T: Encoder]: ObjectEncoder[Node[T]] = semiauto.deriveEncoder[Node[T]]
  implicit def decoder[T: Decoder]: Decoder[Node[T]] = semiauto.deriveDecoder[Node[T]]

  /** Generates a Default Sink. */
  def sink[T](service: T, contentType: Option[String] = None): Node[T] = Node[T](
    service, resources = Map(ExecutorModelDefaults.SinkResource -> NodeResource(ResourceType.Sink, contentType))
  )

  /** Generates a Default Source. */
  def source[T](service: T, contentType: Option[String] = None): Node[T] = Node[T](
    service, resources = Map(ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, contentType))
  )

  /** Generates a Default Transformer. */
  def transformer[T](service: T): Node[T] = Node[T](
    service, resources = Map(ExecutorModelDefaults.TransformationResource -> NodeResource(ResourceType.Transformer))
  )

  implicit def renderable[T](implicit renderable: Renderable[T]): Renderable[Node[T]] = new Renderable[Node[T]] {
    override def buildRenderTree(value: Node[T]): Renderable.RenderTree = {
      val resources = value.resources.toIndexedSeq.sortBy(_._1)
      val resourcesTree = if (resources.isEmpty) {
        Renderable.Leaf("No Resources")
      } else {
        Renderable.SubTree(
          resources.map {
            case (name, resource) =>
              Renderable.Leaf(s"${name} ${resource.resourceType.toString} ${resource.contentType.getOrElse("")}")
          }.toVector,
          title = Some("Resources")
        )
      }
      Renderable.SubTree(
        items = Vector(
          Renderable.buildRenderTree(value.service),
          resourcesTree
        )
      )
    }
  }
}
