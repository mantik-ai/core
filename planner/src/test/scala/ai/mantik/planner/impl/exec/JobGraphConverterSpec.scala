package ai.mantik.planner.impl.exec

import ai.mantik.executor.model.{ Container, ContainerService, DataProvider, ExecutorModelDefaults, ExistingService, Graph, Job, Link, Node, NodeResourceRef, ResourceType }
import ai.mantik.planner.impl.TestItems
import ai.mantik.planner.{ PlanFileReference, PlanNodeService }
import ai.mantik.repository.FileRepository.{ FileGetResult, FileStorageResult }
import ai.mantik.testutils.TestBase
import akka.http.scaladsl.model.Uri

class JobGraphConverterSpec extends TestBase {

  val files = ExecutionOpenFiles(
    remoteFileServiceUri = Uri("http://file-service:1234"),
    readFiles = Map(
      PlanFileReference(0) -> FileGetResult("file0", "files/file0", "resource0", None),
      PlanFileReference(1) -> FileGetResult("file1", "files/file1", "resource1", None)
    ),
    writeFiles = Map(
      PlanFileReference(2) -> FileStorageResult("file2", "files/file2", "resource2")
    )
  )

  val inputGraph = Graph(
    nodes = Map(
      "0" -> Node(
        PlanNodeService.File(PlanFileReference(0)),
        resources = Map(
          ExecutorModelDefaults.SourceResource -> ResourceType.Source
        )
      ),
      "1" -> Node(
        PlanNodeService.DockerContainer("image1", Some(PlanFileReference(1)), TestItems.algorithm1),
        resources = Map(
          "apply" -> ResourceType.Transformer
        )
      ),
      "2" -> Node(
        PlanNodeService.File(PlanFileReference(2)),
        resources = Map(
          ExecutorModelDefaults.SinkResource -> ResourceType.Sink
        )
      )
    ),
    links = Link.links(
      NodeResourceRef("0", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("1", ExecutorModelDefaults.TransformationResource),
      NodeResourceRef("1", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("2", ExecutorModelDefaults.SinkResource)
    )
  )

  "translateGraphIntoJob" should "work" in {
    val got = new JobGraphConverter("space1", files, "my-content-type").translateGraphIntoJob(inputGraph)
    got shouldBe Job(
      isolationSpace = "space1",
      graph = Graph(
        nodes = Map(
          "0" -> Node(
            ExistingService("http://file-service:1234"),
            Map(
              "files/file0" -> ResourceType.Source
            )
          ),
          "1" -> Node(
            ContainerService(
              Container("image1", parameters = Nil),
              dataProvider = Some(
                DataProvider(url = Some("http://file-service:1234/files/file1"), mantikfile = Some(TestItems.algorithm1.toJson), directory = Some("dir1"))
              )
            ),
            Map(
              "apply" -> ResourceType.Transformer
            )
          ),
          "2" -> Node(
            ExistingService("http://file-service:1234"),
            Map(
              "files/file2" -> ResourceType.Sink
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("0", "files/file0") -> NodeResourceRef("1", "apply"),
          NodeResourceRef("1", "apply") -> NodeResourceRef("2", "files/file2")
        )
      ),
      contentType = Some("my-content-type")
    )
  }
}
