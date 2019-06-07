package ai.mantik.repository.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.repository.{ AlgorithmDefinition, Errors, MantikArtifact, MantikId, Mantikfile, Repository }
import ai.mantik.testutils.{ AkkaSupport, TestBase }

/** Common tests for repositories. */
abstract class RepositorySpecBase extends TestBase with AkkaSupport {

  type RepoType <: Repository

  protected def createRepo(): RepoType

  protected def withRepo[T](f: RepoType => T): T = {
    val repo = createRepo()
    try {
      f(repo)
    } finally {
      repo.shutdown()
    }
  }

  val artifact1 = MantikArtifact(
    mantikfile = Mantikfile.pure(AlgorithmDefinition(
      stack = "stack1",
      `type` = FunctionType(
        input = FundamentalType.Int32,
        output = FundamentalType.Int64
      )
    )),
    fileId = Some("1234"),
    id = MantikId(
      "func1",
      "version1"
    )
  )

  val artifact1DifferentVersion = artifact1.copy(
    id = artifact1.id.copy(
      version = "version2"
    )
  )

  val artifact1DifferentName = artifact1.copy(
    id = artifact1.id.copy(
      name = "other_name"
    )
  )

  val artifact2 = MantikArtifact(
    mantikfile = Mantikfile.pure(AlgorithmDefinition(
      stack = "stack2",
      `type` = FunctionType(
        input = FundamentalType.Int32,
        output = FundamentalType.Int64
      )
    )),
    fileId = None,
    id = MantikId(
      "func2",
      "version2"
    )
  )

  it should "store and retrieve artifacts" in {
    withRepo { repo =>
      intercept[Errors.NotFoundException] {
        await(repo.get(artifact1.id))
      }

      await(repo.store(artifact1))
      val back = await(repo.get(artifact1.id))
      back shouldBe artifact1

      intercept[Errors.NotFoundException] {
        await(repo.get(artifact2.id))
      }

      await(repo.store(artifact2))
      val back2 = await(repo.get(artifact2.id))
      back2 shouldBe artifact2

      val back3 = await(repo.get(artifact1.id))
      back3 shouldBe artifact1
    }
  }

  it should "overwrite existing versions" in {
    withRepo { repo =>
      await(repo.store(artifact1))
      val updated = artifact1.copy(
        mantikfile = Mantikfile.pure(
          artifact1.mantikfile.definition.asInstanceOf[AlgorithmDefinition].copy(
            stack = "other_stack"
          )
        )
      )
      await(repo.store(updated))
      await(repo.get(artifact1.id)) shouldBe updated
    }
  }

  it should "allow deleting an artifact" in {
    withRepo { repo =>
      await(repo.store(artifact1))
      await(repo.store(artifact1DifferentName))
      await(repo.store(artifact1DifferentVersion))
      await(repo.remove(MantikId("someId"))) shouldBe false

      await(repo.remove(artifact1.id)) shouldBe true
      intercept[Errors.NotFoundException] {
        await(repo.get(artifact1.id))
      }
      withClue("Other names/versions should still exists") {
        await(repo.get(artifact1DifferentName.id)) shouldBe artifact1DifferentName
        await(repo.get(artifact1DifferentVersion.id)) shouldBe artifact1DifferentVersion
      }
    }
  }
}
