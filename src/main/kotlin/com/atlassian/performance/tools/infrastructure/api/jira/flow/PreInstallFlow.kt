package com.atlassian.performance.tools.infrastructure.api.jira.flow

import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.DefaultPostInstallHook
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.PreInstallHook
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.DefaultPostStartHook
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

open class PreInstallFlow protected constructor() : PostInstallFlow() {

    private val preInstallHooks: Queue<PreInstallHook> = ConcurrentLinkedQueue()

    fun hook(
        hook: PreInstallHook
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

    companion object {
        fun default(): PreInstallFlow = PreInstallFlow()
            .apply { hook(DefaultPostStartHook()) }
            .apply { hook(DefaultPostInstallHook(JiraNodeConfig.Builder().build())) }

        fun empty(): PreInstallFlow = PreInstallFlow()
    }
}
