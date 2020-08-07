package com.atlassian.performance.tools.infrastructure

import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.infrastructure.sshubuntu.SshUbuntuImage.Companion.runSoloSsh
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DockerIT {

    @Test
    fun shouldRunOnVariousUbuntuVersions() {
        val pool = Executors.newCachedThreadPool()
        listOf("18.04", "16.04")
            .map { ubuntuVersion ->
                pool.submitLabelled(ubuntuVersion) {
                    runSoloSsh(ubuntuVersion) { ssh ->
                        //workaround for a bug in Docker download site for bionic
                        Ubuntu().install(ssh, listOf("curl"))
                        val packageFile = "containerd.io_1.2.2-3_amd64.deb"
                        ssh.execute("curl -O https://download.docker.com/linux/ubuntu/dists/bionic/pool/edge/amd64/$packageFile", Duration.ofMinutes(3))
                        ssh.execute("sudo apt install ./$packageFile", Duration.ofMinutes(3))

                        Docker().install(ssh)
                        DockerImage("hello-world").run(ssh)
                    }
                }
            }
            .forEach { it.get() }
    }
}

private fun <T> ExecutorService.submitLabelled(
    label: String,
    task: () -> T
): CompletableFuture<T> {
    return submitWithLogContext(label) {
        try {
            task()
        } catch (e: Exception) {
            throw Exception(label, e)
        }
    }
}
