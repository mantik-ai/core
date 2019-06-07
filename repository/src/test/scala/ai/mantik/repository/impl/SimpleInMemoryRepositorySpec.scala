package ai.mantik.repository.impl

class SimpleInMemoryRepositorySpec extends RepositorySpecBase {
  override type RepoType = SimpleInMemoryRepository

  override protected def createRepo(): SimpleInMemoryRepository = {
    new SimpleInMemoryRepository
  }
}
