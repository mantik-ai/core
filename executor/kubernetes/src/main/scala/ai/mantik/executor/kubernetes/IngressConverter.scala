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
package ai.mantik.executor.kubernetes

import skuber.{ObjectMeta, Service}
import skuber.ext.Ingress

/** Handles ingress conversions for Kubernetes */
case class IngressConverter(
    config: Config,
    kubernetesHost: String,
    ingressName: String
) {

  /** Builds the ingress for a (deployed) service. */
  def ingress(service: Service): Ingress = {
    require(
      service.name.nonEmpty,
      "The service must have a name, either by defining it, or by creating it and using the returned service."
    )

    val annotations = config.kubernetes.ingressAnnotations.mapValues(interpolateIngressString)

    val servicePort = (for {
      spec <- service.spec
      firstPort <- spec.ports.headOption
    } yield firstPort.port).getOrElse(
      throw new IllegalArgumentException("Could not find out port for service")
    )

    Ingress(
      metadata = ObjectMeta(
        name = ingressName,
        labels = service.metadata.labels,
        annotations = annotations
      ),
      spec = Some(
        ingressSpec(service.name, servicePort)
      )
    )
  }

  /** Returns the external URL for the Ingress */
  def ingressUrl: String = {
    interpolateIngressString(config.kubernetes.ingressRemoteUrl)
  }

  private def ingressSpec(serviceName: String, servicePort: Int): Ingress.Spec = {
    val backend =
      Ingress.Backend(
        serviceName = serviceName,
        servicePort = servicePort
      )

    config.kubernetes.ingressSubPath match {
      case Some(subPath) =>
        // Allocating sub path
        val path = interpolateIngressString(subPath)
        Ingress.Spec(
          rules = List(
            Ingress.Rule(
              host = None,
              http = Ingress.HttpRule(
                paths = List(
                  Ingress.Path(
                    path = path,
                    backend = backend
                  )
                )
              )
            )
          )
        )
      case None =>
        // Ingress with direct service
        Ingress.Spec(
          backend = Some(
            backend
          )
        )
    }
  }

  private def interpolateIngressString(in: String): String = {
    in
      .replace("${name}", ingressName)
      .replace("${kubernetesHost}", kubernetesHost)
  }
}
