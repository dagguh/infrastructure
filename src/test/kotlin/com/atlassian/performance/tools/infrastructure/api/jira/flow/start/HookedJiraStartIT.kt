package com.atlassian.performance.tools.infrastructure.api.jira.flow.start

import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.EmptyJiraHome
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.TcpServer
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.*
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.StartedJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.TcpServerHook
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.UbuntuSysstat
import com.atlassian.performance.tools.infrastructure.api.jvm.OracleJDK
import com.atlassian.performance.tools.infrastructure.toSsh
import com.atlassian.performance.tools.ssh.api.SshConnection
import com.atlassian.performance.tools.sshubuntu.api.SshUbuntuContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.function.Consumer

class HookedJiraStartIT {

    @Test
    fun shouldStartJiraWithDefaultHooks() {
        // given
        val config = JiraNodeConfig.Builder().build()
        val flow = JiraNodeFlow()
        flow.hookPostStart(DefaultStartedJiraHook())
        flow.hookPostInstall(DefaultPostInstallHook(config))
        val jiraInstallation = HookedJiraInstallation(DefaultJiraInstallation(
            jiraHomeSource = EmptyJiraHome(),
            productDistribution = PublicJiraSoftwareDistribution("7.13.0"),
            jdk = OracleJDK()
        ))
        val jiraStart = HookedJiraStart(JiraLaunchScript())
        val privatePort = 8080
        val container = SshUbuntuContainer(Consumer {
            it.addExposedPort(privatePort)
        })
        val remoteReports = container.start().use { sshUbuntu ->
            val server = TcpServer(
                "localhost",
                sshUbuntu.container.getMappedPort(privatePort),
                privatePort,
                "my-jira"
            )
            val remoteReports = sshUbuntu.toSsh().newConnection().use { ssh ->
                // when
                val installed = jiraInstallation.install(ssh, server, flow)
                val started = jiraStart.start(ssh, installed, flow)
                stop(started, ssh)
                flow.reports.flatMap { it.locate(ssh) }
            }

            // then
            sshUbuntu.toSsh().newConnection().use { ssh ->
                download(remoteReports, ssh)
            }
            return@use remoteReports
        }

        assertThat(remoteReports).contains(
            "./jpt-vmstat.log",
            "./jpt-iostat.log",
            "jira-home/log/atlassian-jira.log",
            "./atlassian-jira-software-7.13.0-standalone/logs/catalina.out",
            "./jpt-jstat.log"
        )
    }

    @Test
    fun shouldDownloadPartialReportsInCaseOfFailure() {
        // given
        val flow = JiraNodeFlow()
        val sysstat = UbuntuSysstat()
        flow.hookPreInstall(sysstat)
        flow.hookPreInstall(FailingHook())
        val jiraInstallation = HookedJiraInstallation(DefaultJiraInstallation(
            jiraHomeSource = EmptyJiraHome(),
            productDistribution = PublicJiraSoftwareDistribution("7.13.0"),
            jdk = OracleJDK()
        ))
        val privatePort = 8080
        val container = SshUbuntuContainer(Consumer {
            it.addExposedPort(privatePort)
        })
        val remoteReports = container.start().use { sshUbuntu ->
            val server = TcpServer(
                "localhost",
                sshUbuntu.container.getMappedPort(privatePort),
                privatePort,
                "my-jira"
            )
            return@use sshUbuntu.toSsh().newConnection().use useSsh@{ ssh ->
                // when
                try {
                    jiraInstallation.install(ssh, server, flow)
                } catch (e: Exception) {
                    println("Failed: ${e.message}")
                }
                return@useSsh flow.reports.flatMap { it.locate(ssh) }
            }
        }

        // then
        assertThat(remoteReports).contains(
            "./jpt-vmstat.log",
            "./jpt-iostat.log"
        )
    }

    private fun stop(
        started: StartedJira,
        ssh: SshConnection
    ) {
        val installed = started.installed
        ssh.execute("${installed.jdk.use()}; ${installed.installation}/bin/stop-jira.sh")
    }

    private fun download(
        remotes: List<String>,
        ssh: SshConnection
    ): List<File> {
        val downloads = Files.createTempDirectory("apt-infra-test")
        return remotes.map { remote ->
            val local = downloads.resolve("./$remote")
            ssh.download(remote, local)
            return@map local.toFile()
        }
    }
}

private class FailingHook : TcpServerHook, InstalledJiraHook {
    override fun hook(ssh: SshConnection, server: TcpServer, flow: JiraNodeFlow) {
        throw Exception("Failed")
    }

    override fun hook(ssh: SshConnection, jira: InstalledJira, flow: JiraNodeFlow) {
        throw Exception("Failed")
    }
}