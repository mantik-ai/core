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

  import scala.language.implicitConversions
  protected implicit def classToClassProvider(x: Class[_ <: AnyRef]): ProviderLike = ClassProvider(
    x.asInstanceOf[Class[AnyRef]]
  )

  protected implicit def classNameToClassProvider(name: String): ProviderLike = ClassName(name)

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
