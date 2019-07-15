package ai.mantik.planner.repository.impl

import java.time.temporal.ChronoUnit

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, MantikId, Mantikfile }
import ai.mantik.planner.repository
import ai.mantik.planner.repository.{ DeploymentInfo, Errors, MantikArtifact, Repository }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.FakeClock

/** Common tests for repositories. */
abstract class RepositorySpecBase extends TestBaseWithAkkaRuntime {

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

  val artifact1 = repository.MantikArtifact(
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
    ),
    itemId = ItemId.generate()
  )

  val deploymentInfo1 = DeploymentInfo(
    name = "name1",
    internalUrl = "url1",
    externalUrl = Some("external_url1"),
    timestamp = FakeClock.DefaultTime
  )

  val deploymentInfo2 = DeploymentInfo(
    name = "name2",
    internalUrl = "url2",
    timestamp = FakeClock.DefaultTime.plus(1, ChronoUnit.HOURS)
  )

  val artifact1DifferentVersion = artifact1.copy(
    id = artifact1.id.copy(
      version = "version2"
    ),
    itemId = ItemId.generate()
  )

  val artifact1DifferentName = artifact1.copy(
    id = artifact1.id.copy(
      name = "other_name"
    ),
    itemId = ItemId.generate()
  )

  val artifact2 = MantikArtifact(
    mantikfile = Mantikfile.pure(elements.AlgorithmDefinition(
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
    ),
    itemId = ItemId.generate()
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
        ),
        itemId = ItemId.generate()
      )
      await(repo.store(updated))
      await(repo.get(artifact1.id)) shouldBe updated
    }
  }

  it should "fail if trying to overwrite with the same id" in {
    withRepo { repo =>
      await(repo.store(artifact1))
      val updated = artifact1.copy(
        mantikfile = Mantikfile.pure(
          artifact1.mantikfile.definition.asInstanceOf[AlgorithmDefinition].copy(
            stack = "other_stack"
          )
        )
      )
      intercept[Errors.OverwriteNotAllowedException] {
        await(repo.store(updated))
      }
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

  it should "allow writing a deployment info along with the artifact" in {
    withRepo { repo =>
      val artifact1WithDeployment = artifact1.copy(
        deploymentInfo = Some(deploymentInfo1)
      )
      await(repo.store(artifact1WithDeployment))
      await(repo.store(artifact2))

      val back1 = await(repo.get(artifact1.id))
      val back2 = await(repo.get(artifact2.id))
      back1 shouldBe artifact1WithDeployment
      back2 shouldBe artifact2
    }
  }

  it should "allow updating a deployment info" in {
    withRepo { repo =>
      await(repo.setDeploymentInfo(artifact1.itemId, None)) shouldBe false
      await(repo.setDeploymentInfo(artifact1.itemId, Some(deploymentInfo1))) shouldBe false

      await(repo.store(artifact1))
      await(repo.store(artifact2))

      await(repo.setDeploymentInfo(artifact1.itemId, Some(deploymentInfo1))) shouldBe true
      val back1 = await(repo.get(artifact1.id))
      back1 shouldBe artifact1.copy(deploymentInfo = Some(deploymentInfo1))
      await(repo.setDeploymentInfo(artifact1.itemId, Some(deploymentInfo2))) shouldBe true

      val back2 = await(repo.get(artifact1.id))
      back2 shouldBe artifact1.copy(deploymentInfo = Some(deploymentInfo2))

      await(repo.setDeploymentInfo(artifact1.itemId, None))

      val back3 = await(repo.get(artifact1.id))
      back3 shouldBe artifact1

      await(repo.get(artifact2.id)) shouldBe artifact2
    }
  }
}
