/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */ 
package io.strimzi.test.systemtest;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.strimzi.utils.StUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;

public class DrainCleanerST extends AbstractST {

    private static final Logger LOGGER = LogManager.getLogger(DrainCleanerST.class);

    @Test
    void testEvictionRequestOnKafkaPod() {
        final String stsName = "my-cluster-kafka";

        LOGGER.info("Creating dummy pod that will contain \"kafka\" in its name.");
        createStatefulSetAndPDBWithWait(stsName);

        LOGGER.info("Creating eviction request to the pod");
        String podName = kubeClient().listPodsByPrefixInName(NAMESPACE, stsName).get(0).getMetadata().getName();
        kubeClient().getClient().pods().inNamespace(NAMESPACE).withName(podName).evict();

        LOGGER.info("Checking that pod annotations will contain \"strimzi.io/manual-rolling-update: true\"");
        Map<String, String> annotations = kubeClient().namespace(NAMESPACE).getPod(podName).getMetadata().getAnnotations();
        assertNotNull(annotations.get("strimzi.io/manual-rolling-update"));
        assertEquals("true", annotations.get("strimzi.io/manual-rolling-update"));
    }

    @Test
    void testEvictionRequestOnRandomPod() {
        final String stsName = "my-cluster-pulsar";

        LOGGER.info("Creating dummy pod that will not contain \"kafka\" or \"zookeeper\" in its name.");
        createStatefulSetAndPDBWithWait(stsName);

        LOGGER.info("Creating eviction request to the pod");
        String podName = kubeClient().listPodsByPrefixInName(NAMESPACE, stsName).get(0).getMetadata().getName();
        kubeClient().getClient().pods().inNamespace(NAMESPACE).withName(podName).evict();

        LOGGER.info("Checking that pod annotations will not contain \"strimzi.io/manual-rolling-update\"");
        Map<String, String> annotations = kubeClient().namespace(NAMESPACE).getPod(podName).getMetadata().getAnnotations();
        assertNull(annotations.get("strimzi.io/manual-rolling-update"));
    }

    void createStatefulSetAndPDBWithWait(String stsName) {
        StatefulSet statefulSet = new StatefulSetBuilder()
            .withNewMetadata()
                .withName(stsName)
                .withNamespace(NAMESPACE)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .addToMatchLabels("app", stsName)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", stsName)
                        .addToLabels("strimzi.io/kind", "Kafka")
                    .endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("nginx-container")
                            .withImage("nginxinc/nginx-unprivileged")
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();

        PodDisruptionBudget pdb = new PodDisruptionBudgetBuilder()
            .withNewMetadata()
                .withName(stsName + "-pdb")
                .withNamespace(NAMESPACE)
            .endMetadata()
            .withNewSpec()
                .withNewMaxUnavailable(0)
                .withNewSelector()
                    .addToMatchLabels("app", stsName)
                .endSelector()
            .endSpec()
            .build();

        StUtils.createStatefulSetWithWait(statefulSet);
        StUtils.createPodDisruptionBudgetWithWait(pdb);
    }
}
