package com.atlassian.performance.tools.infrastructure.api.jira.report

import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * Reports back about remote events. E.g. points to interesting logs, dumps, charts.
 */
interface Report {

    /**
     * @param [ssh] connects to the server, which holds interesting data
     * @return interesting file paths, which could be downloaded
     */
    fun locate(ssh: SshConnection): List<String>
}