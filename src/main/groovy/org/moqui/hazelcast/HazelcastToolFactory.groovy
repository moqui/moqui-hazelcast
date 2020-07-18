/*
 * This software is in the public domain under CC0 1.0 Universal plus a 
 * Grant of Patent License.
 * 
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 * 
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.hazelcast

import com.hazelcast.aws.AwsDiscoveryStrategyFactory
import com.hazelcast.config.Config
import com.hazelcast.config.DiscoveryStrategyConfig
import com.hazelcast.config.JoinConfig
import com.hazelcast.config.XmlConfigBuilder
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.kubernetes.HazelcastKubernetesDiscoveryStrategyFactory
import groovy.transform.CompileStatic
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** ElasticSearch Client is used for indexing and searching documents */
@CompileStatic
class HazelcastToolFactory implements ToolFactory<HazelcastInstance> {
    protected final static Logger logger = LoggerFactory.getLogger(HazelcastToolFactory.class)
    final static String TOOL_NAME = "Hazelcast"

    protected ExecutionContextFactory ecf = null

    /** Hazelcast Instance */
    protected HazelcastInstance hazelcastInstance = null

    /** Default empty constructor */
    HazelcastToolFactory() { }

    @Override
    String getName() { return TOOL_NAME }
    @Override
    void init(ExecutionContextFactory ecf) { }
    @Override
    void preFacadeInit(ExecutionContextFactory ecf) {
        this.ecf = ecf

        // initialize Hazelcast using hazelcast.xml on the classpath for config unless there is a hazelcast.config system property
        Config hzConfig
        if (System.getProperty("hazelcast.config")) {
            logger.info("Starting Hazelcast with hazelcast.config system property (${System.getProperty("hazelcast.config")})")
            hzConfig = new Config("moqui")
        } else {
            logger.info("Starting Hazelcast with hazelcast.xml from classpath")
            // logger.info(ObjectUtilities.getStreamText(Thread.currentThread().getContextClassLoader().getResourceAsStream("hazelcast.xml")))
            hzConfig = new XmlConfigBuilder(Thread.currentThread().getContextClassLoader().getResourceAsStream("hazelcast.xml")).build()
            hzConfig.setInstanceName("moqui")
        }

        // NOTE: programmatically set various settings instead of using ${} placeholders in hazelcast.xml because Hazelcast uses
        //     placeholder value instead of empty/null if property not specified, ie no way to not set a property using that approach

        if (System.getProperty("hazelcast_multicast_enabled") == "true") {
            logger.info("Found hazelcast_multicast_enabled=true so adding multicast join config")

            JoinConfig joinConfig = hzConfig.getNetworkConfig().getJoin()
            joinConfig.getTcpIpConfig().setEnabled(false)
            joinConfig.getMulticastConfig().setEnabled(true)
            joinConfig.getAwsConfig().setEnabled(false)
            joinConfig.getKubernetesConfig().setEnabled(false)

            joinConfig.multicastConfig.setMulticastGroup(System.getProperty("hazelcast_multicast_group") ?: "224.2.2.3")
            joinConfig.multicastConfig.setMulticastPort((System.getProperty("hazelcast_multicast_port") ?: "54327") as int)
        }
        if (System.getProperty("hazelcast_tcp_ip_enabled") == "true") {
            logger.info("Found hazelcast_tcp_ip_enabled=true so adding tcp/ip join config")

            JoinConfig joinConfig = hzConfig.getNetworkConfig().getJoin()
            joinConfig.getTcpIpConfig().setEnabled(true)
            joinConfig.getMulticastConfig().setEnabled(false)
            joinConfig.getAwsConfig().setEnabled(false)
            joinConfig.getKubernetesConfig().setEnabled(false)

            String tcpIpMembers = System.getProperty("hazelcast_tcp_ip_members")
            if (tcpIpMembers) {
                joinConfig.tcpIpConfig.setMembers(Arrays.asList(tcpIpMembers.split(",")).collect({ it.trim() }))
            } else {
                logger.warn("No hazelcast_tcp_ip_members specified")
            }
        }
        if (System.getProperty("hazelcast_aws_enabled") == "true") {
            logger.info("Found hazelcast_aws_enabled=true so adding AwsDiscoveryStrategy")

            hzConfig.getProperties().setProperty("hazelcast.discovery.enabled", "true")

            JoinConfig joinConfig = hzConfig.getNetworkConfig().getJoin()
            joinConfig.getTcpIpConfig().setEnabled(false)
            joinConfig.getMulticastConfig().setEnabled(false)
            joinConfig.getAwsConfig().setEnabled(false)
            joinConfig.getKubernetesConfig().setEnabled(false)
            AwsDiscoveryStrategyFactory awsDiscoveryStrategyFactory = new AwsDiscoveryStrategyFactory()

            Map<String, Comparable> properties = new HashMap<String, Comparable>()
            if (System.getProperty("hazelcast_aws_access_key")) properties.put("access-key", System.getProperty("hazelcast_aws_access_key"))
            if (System.getProperty("hazelcast_aws_secret_key")) properties.put("secret-key", System.getProperty("hazelcast_aws_secret_key"))
            if (System.getProperty("hazelcast_aws_iam_role")) properties.put("iam-role", System.getProperty("hazelcast_aws_iam_role"))
            if (System.getProperty("hazelcast_aws_region")) properties.put("region", System.getProperty("hazelcast_aws_region"))
            properties.put("host-header",System.getProperty("hazelcast_aws_host_header") ?: "ec2.amazonaws.com")
            if (System.getProperty("hazelcast_aws_security_group")) properties.put("security-group-name", System.getProperty("hazelcast_aws_security_group"))
            if (System.getProperty("hazelcast_aws_tag_key")) properties.put("tag-key", System.getProperty("hazelcast_aws_tag_key"))
            if (System.getProperty("hazelcast_aws_tag_value")) properties.put("tag-value", System.getProperty("hazelcast_aws_tag_value"))
            properties.put("hz-port",System.getProperty("hazelcast_aws_hz_port") ?: "5701")

            DiscoveryStrategyConfig discoveryStrategyConfig = new DiscoveryStrategyConfig(awsDiscoveryStrategyFactory, properties)
            joinConfig.getDiscoveryConfig().addDiscoveryStrategyConfig(discoveryStrategyConfig)
        }
        if (System.getProperty("hazelcast_interfaces_enabled") == "true") {
            logger.info("Found hazelcast_interfaces_enabled=true so enabling interfaces")

            hzConfig.networkConfig.interfaces.setEnabled(true)
            if (System.getProperty("hazelcast_interface1")) hzConfig.networkConfig.interfaces.addInterface(System.getProperty("hazelcast_interface1"))
            if (System.getProperty("hazelcast_interface2")) hzConfig.networkConfig.interfaces.addInterface(System.getProperty("hazelcast_interface2"))
            if (System.getProperty("hazelcast_interface3")) hzConfig.networkConfig.interfaces.addInterface(System.getProperty("hazelcast_interface3"))
        }
        if (System.getProperty("hazelcast_k8s_enabled") == "true") {
            logger.info("Found hazelcast_kubernetes_enabled=true so enabling kubernetes")

            JoinConfig joinConfig = hzConfig.getNetworkConfig().getJoin()
            joinConfig.getTcpIpConfig().setEnabled(false)
            joinConfig.getMulticastConfig().setEnabled(false)
            joinConfig.getAwsConfig().setEnabled(false)
            joinConfig.getKubernetesConfig().setEnabled(true)
            HazelcastKubernetesDiscoveryStrategyFactory k8sDiscoveryStrategyFactory = new HazelcastKubernetesDiscoveryStrategyFactory()

            Map<String, Comparable> properties = new HashMap<String, Comparable>()
            properties.put("namespace", System.getProperty("hazelcast_k8s_namespace") ?: "default")
            if (System.getProperty("hazelcast_k8s_service_name")) properties.put("service-name", System.getProperty("hazelcast_k8s_service_name"))
            if (System.getProperty("hazelcast_k8s_service_label_name")) properties.put("service-label-name", System.getProperty("hazelcast_k8s_service_label_name"))
            if (System.getProperty("hazelcast_k8s_service_label_value")) properties.put("service-label-value", System.getProperty("hazelcast_k8s_service_label_value"))
            if (System.getProperty("hazelcast_k8s_pod_label_name")) properties.put("pod-label-name", System.getProperty("hazelcast_k8s_pod_label_name"))
            if (System.getProperty("hazelcast_k8s_pod_label_value")) properties.put("pod-label-value", System.getProperty("hazelcast_k8s_pod_label_value"))

            DiscoveryStrategyConfig discoveryStrategyConfig = new DiscoveryStrategyConfig(k8sDiscoveryStrategyFactory, properties)
            joinConfig.getDiscoveryConfig().addDiscoveryStrategyConfig(discoveryStrategyConfig)
        }

        hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(hzConfig)
    }

    @Override
    HazelcastInstance getInstance(Object... parameters) {
        if (hazelcastInstance == null) throw new IllegalStateException("HazelcastToolFactory not initialized")
        return hazelcastInstance
    }

    @Override
    void destroy() {
        // shutdown Hazelcast
        Hazelcast.shutdownAll()
        // the above may be better than this: if (hazelcastInstance != null) hazelcastInstance.shutdown()
        logger.info("Hazelcast shutdown")
    }

    ExecutionContextFactory getEcf() { return ecf }
}
