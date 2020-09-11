package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.common.test.integration.StartWorkerSpecBase
import ai.mantik.executor.kubernetes.{K8sOperations, KubernetesConstants}
import skuber.apps.Deployment
import skuber.ext.Ingress
import skuber.json.ext.format._
import skuber.json.format._
import skuber.{Pod, Service}

class StartWorkerSpec extends IntegrationTestBase with StartWorkerSpecBase {


  override protected def checkEmptyNow(): Unit = {
    val ops = new K8sOperations(config, _kubernetesClient)
    val ns = config.kubernetes.namespacePrefix + isolationSpace
    val labelFilter = Seq(
      LabelConstants.UserIdLabelName -> userId
    )
    eventually {
      await(ops.listSelected[Pod](Some(ns), labelFilter)).toList shouldBe empty
      await(ops.listSelected[Service](Some(ns), labelFilter)).toList shouldBe empty
      await(ops.listSelected[Ingress](Some(ns), labelFilter)).toList shouldBe empty
      import skuber.json.apps.format.deployListFormat
      await(ops.listSelected[Deployment](Some(ns), labelFilter)).toList shouldBe empty
    }
  }
}
