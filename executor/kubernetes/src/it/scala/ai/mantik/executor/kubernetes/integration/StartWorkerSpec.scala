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

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.common.test.integration.StartWorkerSpecBase
import ai.mantik.executor.kubernetes.{K8sOperations, KubernetesConstants}
import skuber.apps.v1.Deployment
import skuber.ext.Ingress
import skuber.json.ext.format._
import skuber.json.format._
import skuber.{Pod, Service}

class StartWorkerSpec extends IntegrationTestBase with StartWorkerSpecBase {

  override protected def checkEmptyNow(): Unit = {
    val ops = new K8sOperations(config, _kubernetesClient)
    val ns = config.kubernetes.namespacePrefix + config.common.isolationSpace
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
