package ai.mantik.executor.docker

import ai.mantik.executor.docker.DockerJob.ContainerDefinition
import ai.mantik.executor.docker.NameGenerator.NodeName
import ai.mantik.executor.docker.api.structures.{ CreateContainerHostConfig, CreateContainerRequest }
import ai.mantik.executor.model.{ ContainerService, ExistingService, GraphAnalysis, Job, Node }
import cats.data.State
import cats.implicits._
import io.circe.syntax._

class DockerJobConverter(job: Job, config: DockerExecutorConfig, jobId: String) {

  val DefaultLabels = Map(
    DockerConstants.IsolationSpaceLabelName -> job.isolationSpace,
    DockerConstants.IdLabelName -> jobId,
    DockerConstants.ManagedByLabelName -> DockerConstants.ManabedByLabelValue,
    DockerConstants.TypeLabelName -> DockerConstants.JobType
  )

  val dockerConverter = new DockerConverter(config, DefaultLabels)

  val analysis = new GraphAnalysis(job.graph)

  /** The translated docker job. */
  lazy val dockerJob: DockerJob = {
    generateDockerJob.run(NameGenerator(jobId)).value._2
  }

  private def generateDockerJob: State[NameGenerator, DockerJob] = {
    for {
      workers <- generateWorkerContainers()
      coordinator <- generateCoordinatorContainer()
      payloadProviders <- generatePayloadProviders()
    } yield {
      DockerJob(
        id = jobId,
        workers = workers,
        coordinator = coordinator,
        payloadProviders = payloadProviders
      )
    }
  }

  private lazy val workerNodes: Map[String, Node[ContainerService]] = job.graph.nodes.collect {
    case (name, n @ Node(cs: ContainerService, _)) => {
      val csNode = n.copy(service = cs)
      name -> csNode
    }
  }

  private def generatePayloadProviders(): State[NameGenerator, Vector[ContainerDefinition]] = {
    val generators = for {
      (name, workerNode) <- workerNodes.toVector
      dataProvider <- workerNode.service.dataProvider
    } yield dockerConverter.generatePayloadProvider(name, dataProvider)

    generators.sequence
  }

  private def generateCoordinatorContainer(): State[NameGenerator, ContainerDefinition] = {
    for {
      workerContainerNames <- workerNodes.keys.toVector.map(nodeName => State { s: NameGenerator => s.nodeName(nodeName) }).sequence
      coordinatorName <- State[NameGenerator, NodeName](_.nodeName("job"))
      coordinatorPlan <- CoordinatorPlanBuilder.generateCoordinatorPlan(job)
    } yield {
      val workerLinks = workerContainerNames.map { name =>
        // Format: Container Name alias
        s"${name.containerName}:${name.internalHostName}"
      }
      val serviceLinks = referencedServices().map {
        case (containerName, internalName) =>
          s"${containerName}:${internalName}"
      }.toVector
      val coordinatorPlanEnv = "COORDINATOR_PLAN=" + coordinatorPlan.asJson.noSpaces
      val coordinatorIpEnv = "COORDINATOR_IP=127.0.0.1" // we disable it in effect, there are no sidecars.
      ContainerDefinition(
        name = coordinatorName.containerName,
        mainPort = None,
        pullPolicy = dockerConverter.pullPolicy(config.common.coordinator),
        createRequest = CreateContainerRequest(
          Image = config.common.coordinator.image,
          Cmd = config.common.coordinator.parameters.toVector,
          Labels = DefaultLabels + (
            DockerConstants.RoleLabelName -> DockerConstants.CoordinatorRole
          ),
          Env = Vector(
            coordinatorPlanEnv,
            coordinatorIpEnv
          ),
          HostConfig = CreateContainerHostConfig(
            Links = workerLinks ++ serviceLinks
          )
        )
      )
    }
  }

  private def generateWorkerContainers(): State[NameGenerator, Vector[ContainerDefinition]] = {
    workerNodes.toVector.map {
      case (name, containerServiceNode) =>
        dockerConverter.generateWorkerContainer(name, containerServiceNode.service)
    }.sequence
  }

  /** Returns referenced containers (mapping from container name to internal name) of this job. */
  private def referencedServices(): Map[String, String] = {
    job.graph.nodes.collect {
      case (_, Node(e: ExistingService, _)) =>
        DockerService.detectInternalService(e.url)
    }.flatten.toMap
  }
}
