/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.strimzi.utils.Constants;
import io.strimzi.utils.StUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DrainCleanerST extends AbstractST {

    private static final Logger LOGGER = LogManager.getLogger(DrainCleanerST.class);

    private static final String MANUAL_RU_ANNO = "strimzi.io/manual-rolling-update";

    @Test
    void testEvictionRequestOnKafkaPod() {
        final String stsName = "my-cluster-kafka";

        LOGGER.info("Creating dummy pod that will contain \"kafka\" in its name.");
        createStatefulSetAndPDBWithWait(stsName);

        LOGGER.info("Creating eviction request to the pod");
        String podName = kubeClient().listPodsByPrefixInName(Constants.NAMESPACE, stsName).get(0).getMetadata().getName();
        kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(podName).evict();

        LOGGER.info("Checking that pod annotations will contain \"{}: true\"", MANUAL_RU_ANNO);

        StUtils.waitForAnnotationToAppear(Constants.NAMESPACE, podName, MANUAL_RU_ANNO);

        Map<String, String> annotations = kubeClient().namespace(Constants.NAMESPACE).getPod(podName).getMetadata().getAnnotations();
        assertEquals("true", annotations.get(MANUAL_RU_ANNO));
    }

    @Test
    void testEvictionRequestOnRandomPod() {
        final String stsName = "my-cluster-pulsar";

        LOGGER.info("Creating dummy pod that will not contain \"kafka\" or \"zookeeper\" in its name.");
        createStatefulSetAndPDBWithWait(stsName);

        LOGGER.info("Creating eviction request to the pod");
        String podName = kubeClient().listPodsByPrefixInName(Constants.NAMESPACE, stsName).get(0).getMetadata().getName();
        kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(podName).evict();

        LOGGER.info("Checking that pod annotations will not contain \"{}\"", MANUAL_RU_ANNO);
        StUtils.waitForAnnotationToNotAppear(Constants.NAMESPACE, podName, MANUAL_RU_ANNO);
    }

    void createStatefulSetAndPDBWithWait(String stsName) {
        StatefulSet statefulSet = new StatefulSetBuilder()
            .withNewMetadata()
                .withName(stsName)
                .withNamespace(Constants.NAMESPACE)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .addToMatchLabels("app", stsName)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToAnnotations("dummy-annotation", "some-value")
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
                .withNamespace(Constants.NAMESPACE)
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
