/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.k8s.cluster;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.strimzi.utils.k8s.KubeClient;
import io.strimzi.utils.k8s.cmdClient.KubeCmdClient;
import io.strimzi.utils.k8s.exception.NoClusterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;

/**
 * Abstraction for a Kubernetes cluster, for example {@code oc cluster up} or {@code minikube}.
 */
public interface KubeCluster {

    String ENV_VAR_TEST_CLUSTER = "TEST_CLUSTER";
    Config CONFIG = Config.autoConfigure(System.getenv().getOrDefault("TEST_CLUSTER_CONTEXT", null));

    /** Return true iff this kind of cluster installed on the local machine. */
    boolean isAvailable();

    /** Return true iff this kind of cluster is running on the local machine */
    boolean isClusterUp();

    /** Return a default CMD cmdClient for this kind of cluster. */
    KubeCmdClient defaultCmdClient();

    default KubeClient defaultClient() {
        return new KubeClient(new DefaultOpenShiftClient(CONFIG), "myproject");
    }

    /**
     * Returns the cluster named by the TEST_CLUSTER environment variable, if set, otherwise finds a cluster that's
     * both installed and running.
     * @return The cluster.
     * @throws NoClusterException If no running cluster was found.
     */
    static KubeCluster bootstrap() throws NoClusterException {
        Logger logger = LoggerFactory.getLogger(KubeCluster.class);

        KubeCluster[] clusters = null;
        String clusterName = System.getenv(ENV_VAR_TEST_CLUSTER);
        if (clusterName != null) {
            switch (clusterName.toLowerCase(Locale.ENGLISH)) {
                case "oc":
                    clusters = new KubeCluster[]{new OpenShift()};
                    break;
                case "minikube":
                    clusters = new KubeCluster[]{new Minikube()};
                    break;
                case "kubernetes":
                    clusters = new KubeCluster[]{new Kubernetes()};
                    break;
                default:
                    throw new IllegalArgumentException(ENV_VAR_TEST_CLUSTER + "=" + clusterName + " is not a supported cluster type");
            }
        }
        if (clusters == null) {
            clusters = new KubeCluster[]{new Minikube(), new Kubernetes(), new OpenShift()};
        }
        KubeCluster cluster = null;
        for (KubeCluster kc : clusters) {
            if (kc.isAvailable()) {
                logger.debug("Cluster {} is installed", kc);
                if (kc.isClusterUp()) {
                    logger.debug("Cluster {} is running", kc);
                    cluster = kc;
                    break;
                } else {
                    logger.warn("Cluster {} is not running!", kc);
                }
            } else {
                logger.warn("Cluster {} is not installed!", kc);
            }
        }
        if (cluster == null) {
            throw new NoClusterException(
                    "Unable to find a cluster; tried " + Arrays.toString(clusters));
        }
        logger.info("Using cluster: {}", cluster);
        return cluster;
    }
}
