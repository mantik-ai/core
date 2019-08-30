package ai.mantik.planner.repository.impl

import java.time.temporal.ChronoUnit

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, NamedMantikId, Mantikfile }
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
    f(repo)
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
    namedId = Some(NamedMantikId(
      name = "func1",
      version = "version1"
    )),
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
    namedId = Some(artifact1.namedId.get.copy(
      version = "version2"
    )),
    itemId = ItemId.generate()
  )

  val artifact1DifferentName = artifact1.copy(
    namedId = Some(artifact1.namedId.get.copy(
      name = "other_name"
    )),
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
    namedId = Some(NamedMantikId(
      name = "func2",
      version = "version2"
    )),
    itemId = ItemId.generate()
  )

  it should "store and retrieve artifacts" in {
    withRepo { repo =>
      intercept[Errors.NotFoundException] {
        await(repo.get(artifact1.namedId.get))
      }
      intercept[Errors.NotFoundException] {
        await(repo.get(artifact1.itemId))
      }

      await(repo.store(artifact1))
      val back = await(repo.get(artifact1.namedId.get))
      back shouldBe artifact1

      intercept[Errors.NotFoundException] {
        await(repo.get(artifact2.namedId.get))
      }

      await(repo.store(artifact2))
      val back2 = await(repo.get(artifact2.namedId.get))
      back2 shouldBe artifact2

      val back3 = await(repo.get(artifact1.namedId.get))
      back3 shouldBe artifact1

      withClue("Pure items can also be pulled") {
        await(repo.get(artifact1.itemId)) shouldBe artifact1.copy(namedId = None)
      }
    }
  }

  it should "allow tagging artifacts" in {
    withRepo { repo =>
      await(repo.store(artifact1))
      await(repo.store(artifact2))

      await(repo.ensureMantikId(artifact1.itemId, artifact1.namedId.get)) shouldBe false // already existing

      val newName = NamedMantikId("newname")
      await(repo.ensureMantikId(artifact1.itemId, newName)) shouldBe true

      val back1 = await(repo.get(artifact1.namedId.get))
      back1 shouldBe artifact1

      val back2 = await(repo.get(newName))
      back2.itemId shouldBe back1.itemId
      back2 shouldBe artifact1.copy(namedId = Some(newName))

      // Tricky: Giving a new itemId
      await(repo.ensureMantikId(artifact2.itemId, newName)) shouldBe true
      val back3 = await(repo.get(newName))
      back3.itemId shouldBe artifact2.itemId
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
      await(repo.get(artifact1.namedId.get)) shouldBe updated
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
      intercept[Errors.ConflictException] {
        await(repo.store(updated))
      }
    }
  }

  it should "allow deleting an artifact" in {
    withRepo { repo =>
      await(repo.store(artifact1))
      await(repo.store(artifact1DifferentName))
      await(repo.store(artifact1DifferentVersion))
      await(repo.remove("someId")) shouldBe false

      await(repo.remove(artifact1.namedId.get)) shouldBe true
      intercept[Errors.NotFoundException] {
        await(repo.get(artifact1.namedId.get))
      }
      withClue("Other names/versions should still exists") {
        await(repo.get(artifact1DifferentName.namedId.get)) shouldBe artifact1DifferentName
        await(repo.get(artifact1DifferentVersion.namedId.get)) shouldBe artifact1DifferentVersion
      }
    }
  }

  it should "allow deleting pure items" in {
    withRepo { repo =>
      val rawItem = artifact1.copy(namedId = None)
      await(repo.store(rawItem))
      await(repo.get(artifact1.itemId)) shouldBe rawItem
      await(repo.remove(artifact1.itemId)) shouldBe true
    }
  }

  it should "deny removing the bare element if there is a deployment" in {
    withRepo { repo =>
      val rawItemWithDeployment = artifact1.copy(
        namedId = None,
        deploymentInfo = Some(deploymentInfo1)
      )
      await(repo.store(rawItemWithDeployment))
      intercept[Errors.ConflictException] {
        await(repo.remove(rawItemWithDeployment.itemId))
      }
      await(repo.get(rawItemWithDeployment.itemId)) shouldBe rawItemWithDeployment
    }
  }

  it should "deny removing the bare element if there is a name on it" in {
    withRepo { repo =>
      await(repo.store(artifact1))
      intercept[Errors.ConflictException] {
        await(repo.remove(artifact1.itemId))
      }
      await(repo.get(artifact1.namedId.get)) shouldBe artifact1
    }
  }

  it should "allow writing a deployment info along with the artifact" in {
    withRepo { repo =>
      val artifact1WithDeployment = artifact1.copy(
        deploymentInfo = Some(deploymentInfo1)
      )
      await(repo.store(artifact1WithDeployment))
      await(repo.store(artifact2))

      val back1 = await(repo.get(artifact1.namedId.get))
      val back2 = await(repo.get(artifact2.namedId.get))
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
      val back1 = await(repo.get(artifact1.namedId.get))
      back1 shouldBe artifact1.copy(deploymentInfo = Some(deploymentInfo1))
      await(repo.setDeploymentInfo(artifact1.itemId, Some(deploymentInfo2))) shouldBe true

      val back2 = await(repo.get(artifact1.namedId.get))
      back2 shouldBe artifact1.copy(deploymentInfo = Some(deploymentInfo2))

      await(repo.setDeploymentInfo(artifact1.itemId, None))

      val back3 = await(repo.get(artifact1.namedId.get))
      back3 shouldBe artifact1

      await(repo.get(artifact2.namedId.get)) shouldBe artifact2
    }
  }
}
