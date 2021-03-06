/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.executor.kubernetes.integration

import java.time.Clock
import java.util.UUID
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.kubernetes.{K8sOperations, KubernetesConstants}
import ai.mantik.testutils.tags.IntegrationTest
import play.api.libs.json.Json
import skuber.Pod.Phase
import skuber.api.client.Status
import skuber.apps.v1.Deployment
import skuber.batch.Job
import skuber.json.batch.format._
import skuber.json.format._
import skuber.{Container, K8SException, LabelSelector, ObjectMeta, Pod, RestartPolicy}

import scala.annotation.nowarn
import scala.concurrent.Future

class K8sOperationsSpec extends KubernetesTestBase {

  @nowarn
  trait Env extends super.Env {
    implicit val clock = Clock.systemUTC()
    val k8sOperations = new K8sOperations(config, kubernetesClient)
  }

  // A pod which will hang around
  val longRunningPod = Pod.Spec(
    containers = List(
      Container(
        name = "main",
        image = "mantikai/bridge.select"
      )
    ),
    restartPolicy = RestartPolicy.Never
  )

  val shortRunningPod = Pod.Spec(
    containers = List(
      Container(
        name = "main",
        image = "hello-world"
      )
    ),
    restartPolicy = RestartPolicy.Never
  )

  "ensureNamespace" should "work" in new Env {
    val myNamespace = config.kubernetes.namespacePrefix + "-ensure"
    val namespaces = await(kubernetesClient.getNamespaceNames)
    namespaces should not contain myNamespace
    val client1 = await(k8sOperations.ensureNamespace(myNamespace))

    val namespaces2 = await(kubernetesClient.getNamespaceNames)

    namespaces2 should contain(myNamespace)
    val client2 = withClue("It should not throw if the namespace already exists") {
      await(k8sOperations.ensureNamespace(myNamespace))
    }

    val namespaces3 = await(kubernetesClient.getNamespaceNames)
    namespaces3 should contain(myNamespace)
  }

  "sendKillPatch" should "work" in new Env {
    val podSpec = Pod.apply(
      "pod1",
      Pod.Spec(
        containers = List(
          Container(
            name = "main",
            image = "mantikai/bridge.select"
          )
        ),
        restartPolicy = RestartPolicy.Never
      )
    )
    val ns = config.kubernetes.namespacePrefix + "cancel"
    await(k8sOperations.ensureNamespace(ns))
    val pod = await(k8sOperations.create(Some(ns), podSpec))
    val namespaced = kubernetesClient.usingNamespace(ns)
    val podAgain = eventually {
      val podAgain = await(namespaced.getOption[Pod](pod.name))
      podAgain shouldBe defined
      podAgain.get.status.get.containerStatuses.exists(
        _.state.get.id == "running"
      ) shouldBe true
      podAgain.get
    }
    val reason = "The pod has been marked as being killed because we don't like it."
    await(k8sOperations.sendKillPatch[Pod](podAgain.name, Some(podAgain.namespace), reason))
    val podAgain2 = await(namespaced.get[Pod](pod.name))
    podAgain2.metadata.annotations.get(KubernetesConstants.KillAnnotationName) shouldBe Some(
      reason
    )
  }

  "errorHandling" should "execute a regular function" in new Env {
    await(k8sOperations.errorHandling("wait")(Future.successful(5))) shouldBe 5
    val e = new RuntimeException("Boom")
    awaitException[RuntimeException] {
      k8sOperations.errorHandling("failed")(Future.failed(e))
    } shouldBe e
  }

  val retryException = new K8SException(
    status = Status(
      code = Some(500),
      details = Some(
        Json.obj(
          "retryAfterSeconds" -> 1
        )
      )
    )
  )

  it should "retry if k8s asks so" in new Env {
    var count = 0
    def failTwice(): Future[Int] = {
      count += 1
      if (count < 3) {
        Future.failed(retryException)
      } else {
        Future.successful(5)
      }
    }
    val t0 = System.currentTimeMillis()
    val result = await(k8sOperations.errorHandling("twice")(failTwice()))
    val t1 = System.currentTimeMillis()
    result shouldBe 5
    t1 - t0 shouldBe >=(2000L)
    count shouldBe 3
  }

  it should "stop retrying if it's too much" in new Env {
    var count = 0
    def failAlways(): Future[Int] = {
      count += 1
      Future.failed(retryException)
    }
    val t0 = System.currentTimeMillis()
    awaitException[K8SException] {
      k8sOperations.errorHandling("failAlways")(failAlways())
    } shouldBe retryException
    val t1 = System.currentTimeMillis()
    (t1 - t0) shouldBe >=(3000L)
    count shouldBe 4 // retried 3 times.
  }

  "deploymentSetReplicas" should "work" in new Env {
    import skuber.json.apps.format.depFormat
    val ns = config.kubernetes.namespacePrefix + "scaletest"
    await(k8sOperations.ensureNamespace(ns))

    val labels = Map(
      LabelConstants.InternalIdLabelName -> UUID.randomUUID().toString
    )

    val deployment = Deployment(
      metadata = ObjectMeta(
        generateName = "deplscaletest1",
        labels = labels
      ),
      spec = Some(
        Deployment.Spec(
          selector = LabelSelector(
            LabelSelector
              .IsEqualRequirement(LabelConstants.InternalIdLabelName, labels(LabelConstants.InternalIdLabelName))
          ),
          template = Pod.Template.Spec(
            metadata = ObjectMeta(
              labels = labels
            ),
            spec = Some(longRunningPod.withRestartPolicy(RestartPolicy.Always))
          )
        )
      )
    )
    deployment.spec.get.replicas.get shouldBe 1
    val deployed = await(
      k8sOperations.create(Some(ns), deployment)
    )
    deployed.spec.get.replicas.get shouldBe 1
    val updated = await(
      k8sOperations.deploymentSetReplicas(Some(ns), deployed.name, 2)
    )
    updated.spec.get.replicas.get shouldBe 2
    val getAgain = await(
      k8sOperations.byName[Deployment](Some(ns), deployed.name)
    )
    getAgain.get.spec.get.replicas.get shouldBe 2
  }
}
