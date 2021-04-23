package com.atlassian.performance.tools.infrastructure.api.loadbalancer

import com.atlassian.performance.tools.infrastructure.api.Infrastructure
import com.atlassian.performance.tools.infrastructure.api.Sed
import com.atlassian.performance.tools.infrastructure.api.jira.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.node.JiraNode
import com.atlassian.performance.tools.infrastructure.api.jira.report.Reports
import com.atlassian.performance.tools.infrastructure.api.jira.start.hook.PreStartHook
import com.atlassian.performance.tools.infrastructure.api.jira.start.hook.PreStartHooks
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

class ApacheProxyPlan(
    private val httpPort: Int,
    private val infrastructure: Infrastructure
) : LoadBalancerPlan {

    private val configPath = "/etc/apache2/sites-enabled/000-default.conf"

    override fun materialize(nodes: List<JiraNode>): LoadBalancer {
        val proxyNode = infrastructure.serve(httpPort, "apache-proxy")
        IdempotentAction("Installing and configuring apache load balancer") {
            proxyNode.ssh.newConnection().use { connection ->
                tryToProvision(connection, nodes)
            }
        }.retry(2, ExponentialBackoff(Duration.ofSeconds(5)))
        val balancerEndpoint = URI("http://${proxyNode.privateIp}:$httpPort/")
        nodes.forEach { it.plan.hooks.preStart.insert(InjectProxy(balancerEndpoint)) }
        return ApacheProxy(balancerEndpoint)
    }

    private fun tryToProvision(ssh: SshConnection, nodes: List<JiraNode>) {
        Ubuntu().install(ssh, listOf("apache2"))
        ssh.execute("sudo rm $configPath")
        ssh.execute("sudo touch $configPath")
        val mods = listOf(
            "proxy", "proxy_ajp", "proxy_http", "rewrite", "deflate", "headers", "proxy_balancer", "proxy_connect",
            "proxy_html", "xml2enc", "lbmethod_byrequests"
        )
        ssh.execute("sudo a2enmod ${mods.joinToString(" ")}")
        appendConfig(
            ssh,
            "Header add Set-Cookie \\\"ROUTEID=.%{BALANCER_WORKER_ROUTE}e; path=/\\\" env=BALANCER_ROUTE_CHANGED"
        )
        appendConfig(ssh, "<Proxy balancer://mycluster>")
        nodes.forEachIndexed { index, node ->
            appendConfig(ssh, "\tBalancerMember http://${node.host.publicIp}:${node.host.port} route=$index")
        }
        appendConfig(ssh, "</Proxy>\n")
        appendConfig(ssh, "ProxyPass / balancer://mycluster/ stickysession=ROUTEID")
        appendConfig(ssh, "ProxyPassReverse / balancer://mycluster/ stickysession=ROUTEID")
        ssh.execute("sudo service apache2 restart", Duration.ofMinutes(3))
    }

    private fun appendConfig(connection: SshConnection, line: String) {
        connection.execute("echo \"$line\" | sudo tee -a $configPath")
    }

    private class ApacheProxy(
        override val uri: URI
    ) : LoadBalancer {
        override fun waitUntilHealthy(timeout: Duration) {}
    }

    private class InjectProxy(
        private val proxy: URI
    ) : PreStartHook {
        override fun call(ssh: SshConnection, jira: InstalledJira, hooks: PreStartHooks, reports: Reports) {
            Sed().replace(
                ssh,
                "bindOnInit=\"false\"",
                "bindOnInit=\"false\" scheme=\"http\" proxyName=\"${proxy.host}\" proxyPort=\"${proxy.port}\"",
                "${jira.installation.path}/conf/server.xml"
            )
        }
    }
}