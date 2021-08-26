/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.k8s;

import io.strimzi.utils.k8s.cluster.KubeCluster;
import io.strimzi.utils.k8s.cmdClient.KubeCmdClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A Junit resource which discovers the running cluster and provides an appropriate KubeClient for it,
 * for use with {@code @BeforeAll} (or {@code BeforeEach}.
 * For example:
 * <pre><code>
 *     public static KubeClusterResource testCluster = new KubeClusterResources();
 *
 *     &#64;BeforeEach
 *     void before() {
 *         testCluster.before();
 *     }
 * </code></pre>
 */
public class KubeClusterResource {

    private static final Logger LOGGER = LogManager.getLogger(KubeClusterResource.class);

    private KubeCluster kubeCluster;
    private KubeCmdClient cmdClient;
    private KubeClient client;
    private static KubeClusterResource kubeClusterResource;

    private String namespace;
    private String testNamespace;

    public static synchronized KubeClusterResource getInstance() {
        if (kubeClusterResource == null) {
            kubeClusterResource = new KubeClusterResource();
            initNamespaces();
            LOGGER.info("Cluster default namespace is {}", kubeClusterResource.getNamespace());
            LOGGER.info("Cluster command line client default namespace is {}", kubeClusterResource.getTestNamespace());
        }
        return kubeClusterResource;
    }

    private KubeClusterResource() { }

    private static void initNamespaces() {
        kubeClusterResource.setDefaultNamespace(cmdKubeClient().defaultNamespace());
        kubeClusterResource.setTestNamespace(cmdKubeClient().defaultNamespace());
    }

    public void setTestNamespace(String testNamespace) {
        this.testNamespace = testNamespace;
    }

    public void setDefaultNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets namespace which is used in Kubernetes clients at the moment
     * @return Used namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Provides appropriate CMD client for running cluster
     * @return CMD client
     */
    public static KubeCmdClient<?> cmdKubeClient() {
        return kubeClusterResource.cmdClient().namespace(kubeClusterResource.getNamespace());
    }

    /**
     * Provides appropriate CMD client with expected namespace for running cluster
     * @param inNamespace Namespace will be used as a current namespace for client
     * @return CMD client with expected namespace in configuration
     */
    public static KubeCmdClient<?> cmdKubeClient(String inNamespace) {
        return kubeClusterResource.cmdClient().namespace(inNamespace);
    }

    /**
     * Provides appropriate Kubernetes client for running cluster
     * @return Kubernetes client
     */
    public static KubeClient kubeClient() {
        return kubeClusterResource.client().namespace(kubeClusterResource.getNamespace());
    }

    /**
     * Provides appropriate Kubernetes client with expected namespace for running cluster
     * @param inNamespace Namespace will be used as a current namespace for client
     * @return Kubernetes client with expected namespace in configuration
     */
    public static KubeClient kubeClient(String inNamespace) {
        return kubeClusterResource.client().namespace(inNamespace);
    }

    public KubeCmdClient cmdClient() {
        if (cmdClient == null) {
            cmdClient = cluster().defaultCmdClient();
        }
        return cmdClient;
    }

    public KubeClient client() {
        if (client == null) {
            this.client = cluster().defaultClient();
        }
        return client;
    }

    public KubeCluster cluster() {
        if (kubeCluster == null) {
            kubeCluster = KubeCluster.bootstrap();
        }
        return kubeCluster;
    }

    public String getTestNamespace() {
        return testNamespace;
    }
}
