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
package ai.mantik.componently.di

import ai.mantik.componently.AkkaRuntime
import com.google.inject.AbstractModule
import javax.inject.{Provider, Singleton}

import scala.reflect.ClassTag

/** A Module for a dependency which can be configured via Config file. */
abstract class ConfigurableDependencies(implicit akkaRuntime: AkkaRuntime) extends AbstractModule {

  /** The configuration key for this dependency. */
  protected val configKey: String

  /** Configured variants. */
  protected case class Classes(
      interface: Class[AnyRef],
      versions: Seq[(String, ProviderLike)]
  )

  /** Responsible for retrieving the implementation. */
  protected sealed trait ProviderLike

  /** Retrieves the implementation by class instance. */
  private case class ClassProvider(clazz: Class[AnyRef]) extends ProviderLike

  /** Retrieves the implementation by class name. */
  private case class ClassName(className: String) extends ProviderLike

  /**
    * Retrieves the implementation by providing a full qualified name of a provider clazz.
    * Can be used for lookup of classes outside the current JAR.
    */
  private case class LookupProvider(providerName: String, asSingleton: Boolean = false) extends ProviderLike

  /** To be implemented: different variants which can be injected. */
  protected def variants: Seq[Classes]

  /** Construct one version of Classes. */
  protected def variation[T: ClassTag](versions: (String, ProviderLike)*): Classes = {
    val ct = implicitly[ClassTag[T]]
    Classes(
      ct.runtimeClass.asInstanceOf[Class[AnyRef]],
      versions
    )
  }

  /** Automatically converts a Class-Instanze into a ProviderLike instance. */
  import scala.language.implicitConversions
  protected implicit def classToClassProvider(x: Class[_ <: AnyRef]): ProviderLike = ClassProvider(
    x.asInstanceOf[Class[AnyRef]]
  )

  /** Automatically converts a Class Name into a ProviderLike instance. */
  protected implicit def classNameToClassProvider(name: String): ProviderLike = ClassName(name)

  /** Converts the class name of a [[javax.inject.Provider]]-Instance into a ProviderLike instance. */
  def provider(fullClassName: String, asSingleton: Boolean = false): ProviderLike =
    LookupProvider(fullClassName, asSingleton)

  override final def configure(): Unit = {
    val value = akkaRuntime.config.getString(configKey)
    variants.foreach { classes =>
      val provider = classes.versions
        .collectFirst {
          case (name, provider) if name == value => provider
        }
        .getOrElse {
          throw new RuntimeException(
            s"Invalid value ${value} for ${configKey} for class ${classes.interface.getName}, valid ${classes.versions
              .map(_._1)}"
          )
        }
      bind(classes.interface, provider)
    }
  }

  private def bind(from: Class[AnyRef], provider: ProviderLike): Unit = {
    provider match {
      case ClassProvider(cls) => bind(from).to(cls)
      case ClassName(name) =>
        val classInstance = getClass.getClassLoader.loadClass(name).asInstanceOf[Class[AnyRef]]
        bind(from).to(classInstance)
      case LookupProvider(name, asSingleton) =>
        val providerInstance = getClass.getClassLoader.loadClass(name).asInstanceOf[Class[Provider[AnyRef]]]
        val provideCall = bind(from).toProvider(providerInstance)
        if (asSingleton) {
          provideCall.in(classOf[Singleton])
        }
    }
  }
}
