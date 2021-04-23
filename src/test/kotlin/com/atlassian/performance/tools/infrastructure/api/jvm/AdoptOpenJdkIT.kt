package com.atlassian.performance.tools.infrastructure.api.jvm

import com.atlassian.performance.tools.infrastructure.api.DockerInfrastructure
import org.junit.Test

class AdoptOpenJdkIT {

    @Test
    fun shouldSupportJstat() {
        DockerInfrastructure().use { infra ->
            infra.serveTest().newConnection().use { connection ->
                JstatSupport(AdoptOpenJDK()).shouldSupportJstat(connection)
            }
        }
    }
}