package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.common.test.integration.DeployPipelineSpecBase
import ai.mantik.testutils.HttpSupport
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class DeployPipelineSpec extends IntegrationTestBase with DeployPipelineSpecBase with HttpSupport