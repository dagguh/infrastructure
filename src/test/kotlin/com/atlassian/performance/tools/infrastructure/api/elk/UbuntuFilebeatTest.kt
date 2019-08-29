package com.atlassian.performance.tools.infrastructure.api.elk

import com.atlassian.performance.tools.awsinfrastructure.api.kibana.Kibana
import com.atlassian.performance.tools.infrastructure.api.elk.UbuntuFilebeat.Companion.FILEBEAT_VU_CONFIG_FILE
import com.atlassian.performance.tools.infrastructure.api.elk.UbuntuFilebeat.Companion.FILEBEAT_VU_SUPPORTING_FILES
import com.atlassian.performance.tools.infrastructure.mock.RememberingSshConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.net.URI

class UbuntuFilebeatTest {
    @Test
    fun shouldDownloadConfigureAndStartDuringCallToInstall(){
        val kibana = Kibana(URI("example.com:5601"), listOf(URI("example.com:9200")))

        val ufb = UbuntuFilebeat(kibana, FILEBEAT_VU_CONFIG_FILE, FILEBEAT_VU_SUPPORTING_FILES)
        val ssh = RememberingSshConnection()

        val expectedUploads = mutableListOf(
            "${File(FILEBEAT_VU_CONFIG_FILE).toPath()} -> /tmp/filebeat/filebeat.yml"
        )
        FILEBEAT_VU_SUPPORTING_FILES.forEach { f -> expectedUploads.add("${File(f).toPath()} -> /tmp/filebeat/${File(f).name}") }

        val expectedCommands = listOf(
            // boilerplate (possibly too fragile for this test?)
            "sudo rm -rf /var/lib/apt/lists/*",
            "sudo apt-get update -qq",
            "sudo DEBIAN_FRONTEND=noninteractive apt-get install -qq wget",

            // download and install
            "wget https://artifacts.elastic.co/downloads/beats/filebeat/filebeat-7.3.1-i386.deb -q --server-response",
            "sudo dpkg -i filebeat-7.3.1-i386.deb",

            // configure
            "[ -f /etc/filebeat/filebeat.yml ] && mv /etc/filebeat/filebeat.yml /etc/filebeat/filebeat.yml.orig",
            "touch /etc/filebeat/filebeat.yml",

            // uploads to tmp happen now...

            // cp tmp files to filebeat pathHome
            "sudo cp /tmp/filebeat/filebeat.yml /etc/filebeat/filebeat.yml",
            "sudo cp /tmp/filebeat/filebeat-processor-script-parseDuration.js /etc/filebeat/filebeat-processor-script-parseDuration.js",

            // validate
            "filebeat test config -c /etc/filebeat/filebeat.yml",

            // start
            "sudo filebeat setup --dashboards",
            "sudo service filebeat start"
        )

        ufb.install(ssh)

        assertThat(ssh.commands)
            .containsExactlyElementsOf(
                expectedCommands
            )

        assertThat(ssh.uploads.map { "${it.localSource} -> ${it.remoteDestination}" })
            .containsExactlyElementsOf(
                expectedUploads
            )
    }
}
