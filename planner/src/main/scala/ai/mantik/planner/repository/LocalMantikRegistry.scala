package ai.mantik.planner.repository

import ai.mantik.planner.repository.impl.LocalMantikRegistryImpl
import com.google.inject.ImplementedBy

/** The local Mantik Registry. */
@ImplementedBy(classOf[LocalMantikRegistryImpl])
trait LocalMantikRegistry extends MantikRegistry {

}
