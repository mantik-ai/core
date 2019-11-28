package ai.mantik.planner.util

import ai.mantik.elements.{ BridgeDefinition, ItemId, MantikDefinition, Mantikfile, NamedMantikId }
import ai.mantik.planner.DefinitionSource
import ai.mantik.planner.repository.{ Bridge, ContentTypes }

trait FakeBridges {

  val algoBridge = Bridge(
    DefinitionSource.Loaded(Some(NamedMantikId("algo1")), ItemId("@id1")),
    Mantikfile.pure(
      BridgeDefinition(
        dockerImage = "docker_algo1",
        suitable = Seq(MantikDefinition.AlgorithmKind),
        protocol = 1,
        payloadContentType = Some(ContentTypes.ZipFileContentType)
      )
    )
  )

  val learningBridge = Bridge(
    DefinitionSource.Loaded(Some(NamedMantikId("training1")), ItemId("@id2")),
    Mantikfile.pure(
      BridgeDefinition(
        dockerImage = "docker_training1",
        suitable = Seq(MantikDefinition.AlgorithmKind, MantikDefinition.TrainableAlgorithmKind),
        protocol = 1,
        payloadContentType = Some(ContentTypes.ZipFileContentType)
      )
    )
  )

  val formatBridge = Bridge(
    DefinitionSource.Loaded(Some(NamedMantikId("format1")), ItemId("@id3")),
    Mantikfile.pure(
      BridgeDefinition(
        dockerImage = "docker_format1",
        suitable = Seq(MantikDefinition.AlgorithmKind),
        protocol = 1,
        payloadContentType = Some(ContentTypes.ZipFileContentType)
      )
    )
  )
}
