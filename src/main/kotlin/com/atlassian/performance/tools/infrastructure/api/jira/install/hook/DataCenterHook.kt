package com.atlassian.performance.tools.infrastructure.api.jira.install.hook

import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.infrastructure.api.jira.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.report.Reports
import com.atlassian.performance.tools.ssh.api.SshConnection

class DataCenterHook(
    private val nodeId: String,
    private val sharedHome: SharedHome
) : PostInstallHook {

    override fun call(
        ssh: SshConnection,
        jira: InstalledJira,
        hooks: PostInstallHooks,
        reports: Reports
    ) {
        val localSharedHome = sharedHome.localSharedHome
        sharedHome.mount(ssh)
        val jiraHome = jira.home.path  // TODO what's the difference between localSharedHome and jiraHome? should both be hookable?
        ssh.execute("echo ehcache.object.port = 40011 >> $jiraHome/cluster.properties")
        ssh.execute("echo jira.node.id = $nodeId >> $jiraHome/cluster.properties")
        ssh.execute("echo jira.shared.home = `realpath $localSharedHome` >> $jiraHome/cluster.properties")
    }
}
