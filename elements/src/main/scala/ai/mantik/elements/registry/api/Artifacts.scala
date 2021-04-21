package ai.mantik.elements.registry.api

import java.time.Instant

import ai.mantik.elements.{ItemId, NamedMantikId}
import io.circe.generic.JsonCodec

@JsonCodec
case class ApiGetArtifactResponse(
    // TODO: This is the MantikHeader, not the MantikDefinition
    mantikDefinition: String,
    mantikDefinitionJson: String, // as string so that it doesn't confuse golang's JSON unmarshal
    namedId: Option[NamedMantikId] = None,
    revision: Option[Int] = None,
    kind: String,
    creationTimestamp: Instant,
    uploadState: String,
    fileId: Option[String],
    itemId: ItemId
)

@JsonCodec
case class ApiPrepareUploadRequest(
    namedId: Option[NamedMantikId] = None,
    itemId: ItemId,
    mantikHeader: String,
    hasFile: Boolean
)

@JsonCodec
case class ApiTagRequest(
    itemId: ItemId,
    mantikId: NamedMantikId
)

@JsonCodec
case class ApiTagResponse(
    updated: Boolean // true if updated, false if already existant
)

@JsonCodec
case class ApiPrepareUploadResponse(
    revision: Option[Int] = None
)

@JsonCodec
case class ApiFileUploadResponse(
    fileId: String
)
