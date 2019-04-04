package ai.mantik.core.impl

import ai.mantik.core.{ Action, DataSet, Plan, Source }
import ai.mantik.core.plugins.Plugins
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.executor.model._
import ai.mantik.repository.FileRepository.FileStorageResult
import ai.mantik.repository._
import ai.mantik.repository.impl.SimpleTempFileRepository
import ai.mantik.testutils.{ AkkaSupport, TestBase }

import scala.concurrent.Future
import scala.util.Success
import akka.stream.scaladsl.{ Source => AkkaSource }
import akka.util.ByteString
class PlannerImplSpec extends TestBase with AkkaSupport {

  /** A File Repo which tracks it's calls. */
  class ExtendedFileRepo extends SimpleTempFileRepository {
    case class StorageCalls(
        temporary: Boolean,
        result: FileStorageResult
    )
    val storageCalls = Seq.newBuilder[StorageCalls]
    override def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult] = {
      super.requestFileStorage(temporary).andThen {
        case Success(value) =>
          lock.synchronized {
            storageCalls += StorageCalls(temporary, value)
          }
      }
    }
  }
  // Note in env, because we need to shut it down after each test.
  private var fileRepo: ExtendedFileRepo = _

  override protected def beforeEach(): Unit = {
    fileRepo = new ExtendedFileRepo()
  }

  override protected def afterEach(): Unit = {
    fileRepo.shutdown()
  }

  trait Env {
    val isolationSpace = "test"
    val planner = new PlannerImpl(isolationSpace, fileRepo, Plugins.default)
    implicit val nodeIdGenerator = new NodeIdGenerator()
  }

  val lit = Bundle.build(
    TabularData(
      "x" -> FundamentalType.Int32
    )
  )
    .row(1)
    .result

  "manifestDataSet" should "convert a simple literal source" in new Env {
    val sourcePlan = await(planner.manifestDataSet(
      DataSet.natural(Source.BundleLiteral(lit), lit.model)
    ))
    val lastStorage = fileRepo.storageCalls.result().head
    lastStorage.temporary shouldBe true
    sourcePlan.preplan shouldBe Plan.PushBundle(lit, "1")
    sourcePlan.graph shouldBe Graph(
      Map(
        "1" -> Node(
          ExistingService(lastStorage.result.executorClusterUrl),
          Map(lastStorage.result.resource -> ResourceType.Source)
        )
      )
    )
    sourcePlan.outputs shouldBe Seq(NodeResourceRef("1", lastStorage.result.resource))
  }

  it should "convert a load natural source" in new Env {
    val file1 = await(fileRepo.requestFileStorage(false))
    // Push in some data, otherwise the file calls will fail
    await(AkkaSource.empty[ByteString].runWith(await(fileRepo.storeFile(file1.fileId, FileRepository.MantikBundleContentType))))

    val ds = DataSet.natural(
      Source.Loaded(
        file1.fileId
      ),
      lit.model
    )

    val sourcePlan = await(planner.manifestDataSet(
      ds
    ))

    sourcePlan.preplan shouldBe Plan.Empty
    sourcePlan.graph shouldBe Graph(
      Map(
        "1" -> Node(
          ExistingService(file1.executorClusterUrl),
          Map(
            file1.resource -> ResourceType.Source
          )
        )
      )
    )
    sourcePlan.outputs shouldBe Seq(NodeResourceRef("1", file1.resource))
  }

  "convert" should "convert a simple save action" in new Env {
    val plan = await(planner.convert(
      Action.SaveAction(
        DataSet.natural(Source.BundleLiteral(lit), lit.model), "item1"
      )
    ))
    val files = fileRepo.storageCalls.result()
    files.size shouldBe 1

    val pushFile = files.head
    pushFile.temporary shouldBe false

    plan shouldBe Plan.seq(
      Plan.PushBundle(lit, pushFile.result.fileId),
      Plan.AddMantikItem(
        MantikArtefact(
          Mantikfile.pure(
            DataSetDefinition(
              name = None,
              version = None,
              format = "natural",
              `type` = lit.model
            )
          ),
          Some(pushFile.result.fileId),
          id = MantikId("item1")
        )
      )
    )

    /*

    // File handles are generated asynchronously, so they can be picked up randomly
    // We have two file handles:
    // - where the literal is stored at the beginning
    // - where the literal is stored after moving it.

    val pushCall = plan.asInstanceOf[Plan.Sequential].plans.head.asInstanceOf[Plan.PushBundle]
    val pushFile = files.find(_.result.fileId == pushCall.fileId).get
    val storeFile = files.find(_.result.fileId != pushCall.fileId).get
    pushFile.temporary shouldBe true
    storeFile.temporary shouldBe false

    plan shouldBe Plan.Sequential(
      Seq(
        Plan.PushBundle(lit, pushFile.result.fileId),
        Plan.RunJob(Job(
          isolationSpace,
          Graph(
            Map(
              "1" -> Node(
                ExistingService(pushFile.result.executorClusterUrl),
                Map(
                  pushFile.result.resource -> ResourceType.Source
                )
              ),
              "2" -> Node(
                ExistingService(storeFile.result.executorClusterUrl),
                Map(
                  storeFile.result.resource -> ResourceType.Sink
                )
              )
            ),
            Link.links(
              NodeResourceRef("1", pushFile.result.resource) -> NodeResourceRef("2", storeFile.result.resource)
            ),
          ), contentType = Some(FileRepository.MantikBundleContentType)
        )),
        Plan.AddMantikItem(
          MantikArtefact(
            Mantikfile.pure(
              DataSetDefinition(
                name = None,
                version = None,
                format = "natural",
                `type` = lit.model
              )
            ),
            Some(storeFile.result.fileId),
            id = MantikId("item1")
          )
        )
      )
    )
    */
  }

  it should "convert a simple fetch operation" in new Env {
    val plan = await(planner.convert(
      Action.FetchAction(
        DataSet.natural(Source.BundleLiteral(lit), lit.model)
      )
    ))
    val files = fileRepo.storageCalls.result()
    files.size shouldBe 1
    val file = files.head
    file.temporary shouldBe true

    plan shouldBe Plan.seq(
      Plan.PushBundle(lit, file.result.fileId),
      Plan.PullBundle(lit.model, file.result.fileId)
    )
    /*

    files.size shouldBe 2

    val pushCall = plan.asInstanceOf[Plan.Sequential].plans.head.asInstanceOf[Plan.PushBundle]
    val pushFile = files.find(_.result.fileId == pushCall.fileId).get
    val fetchFile = files.find(_.result.fileId != pushCall.fileId).get
    pushFile.temporary shouldBe true
    fetchFile.temporary shouldBe true

    plan shouldBe Plan.Sequential(
      Seq(
        Plan.PushBundle(lit, pushFile.result.fileId),
        Plan.RunJob(Job(
          isolationSpace,
          Graph(
            Map(
              "1" -> Node(
                ExistingService(pushFile.result.executorClusterUrl),
                Map(
                  pushFile.result.resource -> ResourceType.Source
                )
              ),
              "2" -> Node(
                ExistingService(fetchFile.result.executorClusterUrl),
                Map(
                  fetchFile.result.resource -> ResourceType.Sink
                )
              )
            ),
            Link.links(
              NodeResourceRef("1", pushFile.result.resource) -> NodeResourceRef("2", fetchFile.result.resource)
            )
          ),
          contentType = Some(FileRepository.MantikBundleContentType))
        ),
        Plan.PullBundle(
          lit.model, fetchFile.result.fileId
        )
      )
    )
    */
  }
}
