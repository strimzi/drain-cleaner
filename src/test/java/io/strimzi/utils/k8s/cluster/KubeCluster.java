/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.k8s.cluster;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.strimzi.utils.k8s.KubeClient;
import io.strimzi.utils.k8s.exception.NoClusterException;
import io.strimzi.utils.k8s.cmdClient.KubeCmdClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Abstraction for a Kubernetes cluster, for example {@code oc cluster up} or {@code minikube}.
 */
public interface KubeCluster {

    Config CONFIG = Config.autoConfigure(null);

    /** Return true iff this kind of cluster installed on the local machine. */
    boolean isAvailable();

    /** Return true iff this kind of cluster is running on the local machine */
    boolean isClusterUp();

    /** Return a default CMD cmdClient for this kind of cluster. */
    KubeCmdClient defaultCmdClient();

    default KubeClient defaultClient() {
        return new KubeClient(new DefaultKubernetesClient(), "default");
    }

    /**
     * Returns the cluster named by the TEST_CLUSTER environment variable, if set, otherwise finds a cluster that's
     * both installed and running.
     * @return The cluster.
     * @throws NoClusterException If no running cluster was found.
     */
    static KubeCluster bootstrap() throws NoClusterException {
        Logger logger = LogManager.getLogger(KubeCluster.class);

        KubeCluster cluster = new Kubernetes();
        if (cluster.isAvailable()) {
            logger.debug("kubectl is installed");
            if (cluster.isClusterUp()) {
                logger.debug("Cluster is running");
            } else {
                throw new NoClusterException("Cluster is not running");
            }
        } else {
            throw new NoClusterException("Unable to find a cluster");
        }

        return cluster;
    }
}
