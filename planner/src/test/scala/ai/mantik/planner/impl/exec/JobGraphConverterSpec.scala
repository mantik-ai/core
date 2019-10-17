package ai.mantik.planner.impl.exec

import ai.mantik.executor.model.docker.{ Container, DockerLogin }
import ai.mantik.executor.model.{ ContainerService, DataProvider, ExecutorModelDefaults, ExistingService, Graph, Job, Link, Node, NodeResource, NodeResourceRef, ResourceType }
import ai.mantik.planner.impl.TestItems
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.repository.FileRepository.{ FileGetResult, FileStorageResult }
import ai.mantik.planner.{ PlanFileReference, PlanNodeService }
import ai.mantik.testutils.TestBase
import akka.http.scaladsl.model.Uri

class JobGraphConverterSpec extends TestBase {

  val fileServiceUri = Uri("http://file-service:1234")

  val files = ExecutionOpenFiles(
    readFiles = Map(
      PlanFileReference(0) -> FileGetResult("file0", false, "files/file0", None),
      PlanFileReference(1) -> FileGetResult("file1", false, "files/file1", None)
    ),
    writeFiles = Map(
      PlanFileReference(2) -> FileStorageResult("file2", "files/file2")
    )
  )

  val inputGraph = Graph(
    nodes = Map(
      "0" -> Node(
        PlanNodeService.File(PlanFileReference(0)),
        resources = Map(
          ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
        )
      ),
      "1" -> Node(
        PlanNodeService.DockerContainer(Container("image1"), Some(PlanFileReference(1)), TestItems.algorithm1),
        resources = Map(
          "apply" -> NodeResource(ResourceType.Transformer, Some(ContentTypes.MantikBundleContentType))
        )
      ),
      "2" -> Node(
        PlanNodeService.File(PlanFileReference(2)),
        resources = Map(
          ExecutorModelDefaults.SinkResource -> NodeResource(ResourceType.Sink, Some(ContentTypes.MantikBundleContentType))
        )
      )
    ),
    links = Link.links(
      NodeResourceRef("0", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("1", ExecutorModelDefaults.TransformationResource),
      NodeResourceRef("1", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("2", ExecutorModelDefaults.SinkResource)
    )
  )

  "translateGraphIntoJob" should "work" in {
    val got = new JobGraphConverter(fileServiceUri, "space1", files, Seq(
      DockerLogin("repo1", "user1", "password1")
    )).translateGraphIntoJob(inputGraph)
    got shouldBe Job(
      isolationSpace = "space1",
      graph = Graph(
        nodes = Map(
          "0" -> Node(
            ExistingService("http://file-service:1234"),
            Map(
              "files/file0" -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
            )
          ),
          "1" -> Node(
            ContainerService(
              Container("image1", parameters = Nil),
              dataProvider = Some(
                DataProvider(url = Some("http://file-service:1234/files/file1"), mantikfile = Some(TestItems.algorithm1.toJson))
              )
            ),
            Map(
              "apply" -> NodeResource(ResourceType.Transformer, Some(ContentTypes.MantikBundleContentType))
            )
          ),
          "2" -> Node(
            ExistingService("http://file-service:1234"),
            Map(
              "files/file2" -> NodeResource(ResourceType.Sink, Some(ContentTypes.MantikBundleContentType))
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("0", "files/file0") -> NodeResourceRef("1", "apply"),
          NodeResourceRef("1", "apply") -> NodeResourceRef("2", "files/file2")
        )
      ),
      extraLogins = Seq(
        DockerLogin("repo1", "user1", "password1")
      )
    )
  }
}
