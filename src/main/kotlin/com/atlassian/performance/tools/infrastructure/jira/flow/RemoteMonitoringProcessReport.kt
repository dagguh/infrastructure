package com.atlassian.performance.tools.infrastructure.jira.flow

import com.atlassian.performance.tools.infrastructure.api.jira.flow.report.Report
import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class RemoteMonitoringProcessReport(
    private val process: RemoteMonitoringProcess
) : Report {
    override fun locate(ssh: SshConnection): List<String> {
        process.stop(ssh)
        return listOf(process.getResultPath())
    }
}