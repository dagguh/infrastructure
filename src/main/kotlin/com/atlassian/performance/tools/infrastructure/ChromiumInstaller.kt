package com.atlassian.performance.tools.infrastructure

import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

internal class ChromiumInstaller(private val uri: URI) {
    internal fun install(ssh: SshConnection) {
        ParallelExecutor().execute(
            { installDependencies(ssh) },
            { installChromium(ssh) }
        )
    }

    private fun installChromium(ssh: SshConnection) {
        HttpResource(uri).download(ssh, "chromium.zip")
        Ubuntu().install(
            ssh,
            listOf("unzip")
        )
        ssh.execute("unzip chromium.zip")
        ssh.execute("sudo ln -s `pwd`/chrome-linux/chrome /usr/bin/chrome")
    }

    private fun installDependencies(ssh: SshConnection) {
        Ubuntu().install(
            ssh,
            listOf(
                "libx11-xcb1",
                "libxcomposite1",
                "libxdamage1",
                "libxi6",
                "libxtst6",
                "libnss3",
                "libcups2",
                "libxss1",
                "libxrandr2",
                "libasound2",
                "libpango1.0",
                "libatk1.0-0",
                "libatk-bridge2.0",
                "libgtk-3-0"
            ),
            Duration.ofMinutes(10)
        )
    }
}
