package ai.mantik.executor.integration

import ai.mantik.executor.impl.KubernetesConstants
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, DeployServiceRequest, DeployedServicesEntry, DeployedServicesQuery, ExecutorModelDefaults, ExistingService, Graph, Job, JobState, Link, Node, NodeResourceRef }
import ai.mantik.testutils.tags.IntegrationTest
import skuber.{ ListResource, Secret }
import skuber.apps.v1.ReplicaSet
import skuber.json.format._

@IntegrationTest
class DeployServiceSpec extends IntegrationTestBase {

  // this seems to be missing in skuber
  implicit val rsListFormat = skuber.json.format.ListResourceFormat[ReplicaSet]

  it should "allow deploying a service" in new Env {
    val isolationSpace = "deploy-spec"
    val deployRequest = DeployServiceRequest(
      "service1",
      isolationSpace = isolationSpace,
      nodeService = ContainerService(
        main = Container(
          image = "executor_sample_transformer"
        )
      )
    )
    val response = await(executor.deployService(deployRequest))
    response.serviceName shouldNot be(empty)
    response.url shouldNot be(empty)

    // we can now use the URL in a job.
    val job = Job(
      isolationSpace = isolationSpace,
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "executor_sample_source"
              )
            )
          ),
          "B" -> Node.transformer(
            ExistingService(
              url = response.url
            )
          ),
          "C" -> Node.sink(
            ContainerService(
              main = Container(
                image = "executor_sample_sink"
              )
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.TransformationResource),
          NodeResourceRef("B", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("C", ExecutorModelDefaults.SinkResource)
        )
      )
    )
    val jobId = await(executor.schedule(job))

    val status = eventually {
      val status = await(executor.status(job.isolationSpace, jobId))
      status.state shouldBe JobState.Finished
      status
    }
  }

  it should "allow querying a service" in new Env {
    val isolationSpace = "deploy-spec2"
    val deployRequest = DeployServiceRequest(
      "service1",
      Some("name1"),
      isolationSpace = isolationSpace,
      nodeService = ContainerService(
        main = Container(
          image = "executor_sample_transformer"
        )
      )
    )
    val response = await(executor.deployService(deployRequest))
    response.serviceName shouldNot be(empty)
    response.url shouldNot be(empty)

    val queryResponse1 = await(executor.queryDeployedServices(
      DeployedServicesQuery(isolationSpace)
    ))

    queryResponse1.services shouldBe List(
      DeployedServicesEntry("service1", response.url)
    )

    // different namesapce
    val queryResponse2 = await(executor.queryDeployedServices(
      DeployedServicesQuery("other-space")
    ))
    queryResponse2.services shouldBe empty

    // different id
    val queryResponse4 = await(executor.queryDeployedServices(
      DeployedServicesQuery(isolationSpace, serviceId = Some("otehr"))
    ))
    queryResponse4.services shouldBe empty

    // over specified (but matching)
    val queryResponse5 = await(executor.queryDeployedServices(
      DeployedServicesQuery(isolationSpace, serviceId = Some("service1"))
    ))
    queryResponse5 shouldBe queryResponse1
  }

  it should "allow service deletion" in new Env {
    val isolationSpace = "deploy-spec3"
    val ns = config.namespacePrefix + isolationSpace
    val nsClient = kubernetesClient.usingNamespace(ns)

    def replicaSetCount(): Int = {
      await(nsClient.list[ListResource[ReplicaSet]]()).size
    }

    def secretCount(): Int = {
      // there is also one default token secret inside minikube.
      await(nsClient.list[ListResource[Secret]]()).count(_.metadata.labels.contains(KubernetesConstants.TrackerIdLabel))
    }

    val deployRequest = DeployServiceRequest(
      "service1",
      isolationSpace = isolationSpace,
      nodeService = ContainerService(
        main = Container(
          image = "executor_sample_transformer"
        )
      )
    )

    val deployRequest2 = DeployServiceRequest(
      "service2",
      isolationSpace = isolationSpace,
      nodeService = ContainerService(
        main = Container(
          image = "executor_sample_transformer"
        )
      )
    )

    val response = await(executor.deployService(deployRequest))
    response.serviceName shouldNot be(empty)
    response.url shouldNot be(empty)

    val response2 = await(executor.deployService(deployRequest2))
    response2.serviceName shouldNot be(empty)
    response2.url shouldNot be(empty)

    replicaSetCount() shouldBe 2
    secretCount() shouldBe 2

    val queryResponse1 = await(executor.queryDeployedServices(
      DeployedServicesQuery(isolationSpace)
    ))

    queryResponse1.services.map(_.serviceId) should contain theSameElementsAs Seq(
      "service1", "service2"
    )

    // non existing isolation space
    val deleteResponse1 = await(executor.deleteDeployedServices(
      DeployedServicesQuery(
        isolationSpace = "other"
      )
    ))
    deleteResponse1 shouldBe 0

    replicaSetCount() shouldBe 2
    secretCount() shouldBe 2

    // not existing id
    val deleteResponse3 = await(executor.deleteDeployedServices(
      DeployedServicesQuery(
        isolationSpace,
        serviceId = Some("otherId")
      )
    ))
    deleteResponse3 shouldBe 0

    // correct id
    val deleteResponse4 = await(executor.deleteDeployedServices(
      DeployedServicesQuery(
        isolationSpace,
        serviceId = Some("service2")
      )
    ))
    deleteResponse4 shouldBe 1

    replicaSetCount() shouldBe 1
    secretCount() shouldBe 1

    // all
    val deleteResponse5 = await(executor.deleteDeployedServices(
      DeployedServicesQuery(
        isolationSpace
      )
    ))
    deleteResponse5 shouldBe 1

    await(executor.queryDeployedServices(DeployedServicesQuery(isolationSpace))).services shouldBe empty
    replicaSetCount() shouldBe 0
    secretCount() shouldBe 0
  }
}
