package ai.mantik.executor.docker.api.structures

import io.circe.generic.JsonCodec

@JsonCodec
case class CreateContainerRequest(
    Image: String,
    Cmd: Vector[String] = Vector.empty,
    Labels: Map[String, String] = Map.empty,
    Env: Vector[String] = Vector.empty, // format is key=value,
    Volumes: Map[String, CreateContainerVolumeEntry] = Map.empty,
    HostConfig: CreateContainerHostConfig = CreateContainerHostConfig(),
    NetworkingConfig: CreateContainerNetworkingConfig = CreateContainerNetworkingConfig()
) {
  /** Add a network association */
  def withNetwork(
    networkName: String,
    createContainerNetworkSpecificConfig: CreateContainerNetworkSpecificConfig): CreateContainerRequest = {
    copy(
      NetworkingConfig = NetworkingConfig.copy(
        EndpointsConfig = NetworkingConfig.EndpointsConfig + (networkName -> createContainerNetworkSpecificConfig)
      )
    )
  }

  def withNetworkId(networkName: String, networkId: String): CreateContainerRequest = {
    withNetwork(
      networkName, CreateContainerNetworkSpecificConfig(
      NetworkID = Some(networkId)
    )
    )
  }
}

@JsonCodec
case class CreateContainerVolumeEntry(
    empty: Option[Int] = None // serialization of empty classes is buggy
)

@JsonCodec
case class CreateContainerHostConfig(
    // Format: container_name[:<ro|rw>]
    VolumesFrom: Vector[String] = Vector.empty,
    // Format container_name:alias.
    Links: Vector[String] = Vector.empty,
    GroupAdd: Vector[String] = Vector.empty,
    PortBindings: Map[String, Vector[PortBindingHost]] = Map.empty, // Key is like 80/tcp
    Binds: Option[Vector[String]] = None,
    RestartPolicy: Option[RestartPolicy] = None
)

@JsonCodec
case class PortBindingHost(
    HostIp: Option[String] = None,
    HostPort: String
)

@JsonCodec
case class RestartPolicy(
    Name: String,
    MaximumRetryCount: Option[Int] = None
)

@JsonCodec
case class CreateContainerNetworkingConfig(
    EndpointsConfig: Map[String, CreateContainerNetworkSpecificConfig] = Map.empty
)

@JsonCodec
case class CreateContainerNetworkSpecificConfig(
    NetworkID: Option[String] = None
)

@JsonCodec
case class CreateContainerResponse(
    Id: String,
    Warnings: Option[Vector[String]] = None
)