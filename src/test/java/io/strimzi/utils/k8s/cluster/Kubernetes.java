/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.k8s.cluster;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.strimzi.utils.executor.Exec;
import io.strimzi.utils.k8s.KubeClient;
import io.strimzi.utils.k8s.cmdClient.KubeCmdClient;
import io.strimzi.utils.k8s.cmdClient.Kubectl;
import io.strimzi.utils.k8s.exception.KubeClusterException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link KubeCluster} implementation for any {@code Kubernetes} cluster.
 */
public class Kubernetes implements KubeCluster {

    public static final String CMD = "kubectl";
    private static final Logger LOGGER = LogManager.getLogger(Kubernetes.class);

    @Override
    public boolean isAvailable() {
        return Exec.isExecutableOnPath(CMD);
    }

    @Override
    public boolean isClusterUp() {
        List<String> cmd = Arrays.asList(CMD, "cluster-info");
        try {
            return Exec.exec(cmd).exitStatus();
        } catch (KubeClusterException e) {
            LOGGER.debug("'" + String.join(" ", cmd) + "' failed. Please double check connectivity to your cluster!");
            LOGGER.debug(String.valueOf(e));
            return false;
        }
    }

    @Override
    public KubeCmdClient defaultCmdClient() {
        return new Kubectl();
    }

    @Override
    public KubeClient defaultClient() {
        return new KubeClient(new KubernetesClientBuilder().build(), "default");
    }

    public String toString() {
        return CMD;
    }
}
