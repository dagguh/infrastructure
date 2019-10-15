package com.atlassian.performance.tools.infrastructure.api.jira.flow

import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJiraHook
import com.atlassian.performance.tools.infrastructure.api.jira.flow.report.Report
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.StartedJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.TcpServerHook
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.StartedJiraHook
import com.atlassian.performance.tools.ssh.api.SshConnection
import net.jcip.annotations.ThreadSafe
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

@ThreadSafe
class JiraNodeFlow {

    private val preInstallHooks: Queue<TcpServerHook> = ConcurrentLinkedQueue()
    private val postInstallHooks: Queue<InstalledJiraHook> = ConcurrentLinkedQueue()
    private val preStartHooks: Queue<InstalledJiraHook> = ConcurrentLinkedQueue()
    private val postStartHooks: Queue<StartedJiraHook> = ConcurrentLinkedQueue()
    val reports: Queue<Report> = ConcurrentLinkedQueue()

    fun hookPreInstall(
        hook: TcpServerHook
    ) {
        preInstallHooks.add(hook)
    }

    internal fun runPreInstallHooks(
        ssh: SshConnection,
        server: TcpServer
    ) {
        while (true) {
            preInstallHooks
                .poll()
                ?.run(ssh, server, this)
                ?: break
        }
    }

    fun hookPostInstall(
        hook: InstalledJiraHook
    ) {
        postInstallHooks.add(hook)
    }

    internal fun runPostInstallHooks(
        ssh: SshConnection,
        jira: InstalledJira
    ) {
        while (true) {
            postInstallHooks
                .poll()
                ?.run(ssh, jira, this)
                ?: break
        }
    }

    fun hookPreStart(
        hook: InstalledJiraHook
    ) {
        preStartHooks.add(hook)
    }

    internal fun runPreStartHooks(
        ssh: SshConnection,
        jira: InstalledJira
    ) {
        while (true) {
            preStartHooks
                .poll()
                ?.run(ssh, jira, this)
                ?: break
        }
    }

    fun hookPostStart(
        hook: StartedJiraHook
    ) {
        postStartHooks.add(hook)
    }

    internal fun runPostStartHooks(
        ssh: SshConnection,
        jira: StartedJira
    ) {
        while (true) {
            postStartHooks
                .poll()
                ?.run(ssh, jira, this)
                ?: break
        }
    }

    fun copy(): JiraNodeFlow = JiraNodeFlow()
        .also { it.preInstallHooks += this.preInstallHooks }
        .also { it.postInstallHooks += this.postInstallHooks }
        .also { it.preStartHooks += this.preStartHooks }
        .also { it.postStartHooks += this.postStartHooks }
        .also { it.reports += this.reports }
}
