package com.atlassian.performance.tools.infrastructure.api.os

import com.atlassian.performance.tools.infrastructure.Iostat
import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.ssh.api.SshConnection
import net.jcip.annotations.ThreadSafe
import org.apache.logging.log4j.Level
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@ThreadSafe
class Ubuntu {

    private companion object {
        private val LOCKS = ConcurrentHashMap<String, Any>()
    }

    fun install(
        ssh: SshConnection,
        packages: List<String>,
        timeout: Duration = Duration.ofMinutes(1)
    ) {
        IdempotentAction("install $packages") {
            tryInstalling(
                ssh,
                packages,
                timeout
            )
        }
            .retry(
                maxAttempts = 2,
                backoff = ExponentialBackoff(
                    baseBackoff = Duration.ofSeconds(5)
                )
            )
    }

    private fun tryInstalling(
        ssh: SshConnection,
        packages: List<String>,
        timeout: Duration
    ) {
        val joinedPackages = packages.joinToString(separator = " ")
        val lock = LOCKS.computeIfAbsent(ssh.getHost().ipAddress) { Object() }
        synchronized(lock) {
            try {
                ssh.execute("sudo apt-get update -qq", Duration.ofMinutes(2))
                ssh.execute(
                    cmd = "sudo DEBIAN_FRONTEND=noninteractive apt-get install -qq $joinedPackages",
                    timeout = timeout,
                    stdout = Level.TRACE,
                    stderr = Level.TRACE
                )
            } catch (e: Exception) {
                ssh.execute("sudo rm -rf /var/lib/apt/lists/*")
                ssh.execute("kill -9 \$(ps x | grep apt-get | cut -d' ' -f2 | tail -n +2)")
                throw Exception("Failed an attempt to install $packages", e)
            }
        }
    }

    fun metrics(
        connection: SshConnection
    ): List<OsMetric> {
        install(connection, listOf("sysstat"))

        return listOf(
            Vmstat(),
            Iostat()
        )
    }
}
