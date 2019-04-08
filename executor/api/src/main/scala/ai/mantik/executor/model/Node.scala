package ai.mantik.executor.model

import io.circe.generic.JsonCodec

/** Defines how a container is going to be started. */
@JsonCodec
case class Container(
    image: String,
    parameters: Seq[String] = Nil
)

/** Something which provides the Node functionality, either a container or an existing service. */
@JsonCodec
sealed trait NodeService

/**
 * Defines a service, which is using contains for providing it.
 *
 * @param main the main container, whose port will be accessed
 * @param dataProvider describes initialisation of the side car.
 * @param port the port where resources can be found from the main container.
 * @param ioAffine flag, that the service is mainly doing IO, which prefers placement near sources/sinks.
 */
case class ContainerService(
    main: Container,
    dataProvider: Option[DataProvider] = None,
    port: Int = ExecutorModelDefaults.Port,
    ioAffine: Boolean = false
) extends NodeService

/**
 * Describes how to initialize a container
 * @param url Zip file URL which is unpacked to /data
 * @param mantikfile Mantikfile content which is put to /data/Mantikfile
 * @param directory if set, the content of the zip file is put to /data/directory-name.
 */
@JsonCodec
case class DataProvider(
    url: Option[String] = None,
    mantikfile: Option[String] = None,
    directory: Option[String] = None
)

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
 */
@JsonCodec
case class Node(
    service: NodeService,
    resources: Map[String, ResourceType]
)

object Node {

  /** Generates a Default Sink. */
  def sink(service: NodeService): Node = Node(
    service, resources = Map(ExecutorModelDefaults.SinkResource -> ResourceType.Sink)
  )

  /** Generates a Default Source. */
  def source(service: NodeService): Node = Node(
    service, resources = Map(ExecutorModelDefaults.SourceResource -> ResourceType.Source)
  )

  /** Generates a Default Transformer. */
  def transformer(service: NodeService): Node = Node(
    service, resources = Map(ExecutorModelDefaults.TransformationResource -> ResourceType.Transformer)
  )
}
