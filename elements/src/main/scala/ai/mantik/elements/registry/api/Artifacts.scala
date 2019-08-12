package ai.mantik.elements.registry.api

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec // TODO: This will probably change.
case class ApiGetArtifactResponse(
    mantikDefinition: String,
    mantikDefinitionJson: String, // as string so that it doesn't confuse golang's JSON unmarshal
    account: String,
    name: String,
    version: String,
    revision: Int,
    kind: String,
    creationTimestamp: Instant,
    uploadState: String,
    fileId: Option[String],
    itemId: String
)

@JsonCodec
case class ApiPrepareUploadRequest(
    mantikId: Option[String] = None,
    itemId: String,
    mantikfile: String,
    hasFile: Boolean
)

@JsonCodec
case class ApiTagRequest(
    itemId: String,
    mantikId: String
)

@JsonCodec
case class ApiTagResponse(
    updated: Boolean // true if updated, false if already existant
)

@JsonCodec
case class ApiPrepareUploadResponse(
    itemId: String
)

@JsonCodec
case class ApiFileUploadResponse(
    fileId: String
)
