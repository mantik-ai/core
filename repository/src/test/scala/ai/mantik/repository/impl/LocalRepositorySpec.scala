package ai.mantik.repository.impl

import ai.mantik.testutils.{ AkkaSupport, TempDirSupport, TestBase }
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._

class LocalRepositorySpec extends RepositorySpecBase with TempDirSupport {

  override type RepoType = LocalRepository

  override protected def createRepo(): LocalRepository = {
    val config = ConfigFactory.parseMap(
      Map(
        "mantik.repository.artifactRepository.local.directory" -> tempDirectory.toString
      ).asJava
    )
    new LocalRepository(config)
  }
}
