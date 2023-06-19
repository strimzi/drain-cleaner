/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.fabric8.kubernetes.api.model.DeleteOptionsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.EvictionBuilder;
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
        final String stsName = "my-cluster-foo";
        final Map<String, String> labels = Map.of(
                "app", stsName,
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );

        LOGGER.info("Creating dummy pod. ");
        createStatefulSetAndPDBWithWait(stsName, labels);

        LOGGER.info("Creating eviction request to the pod");
        ObjectMeta meta = kubeClient().listPodsByPrefixInName(Constants.NAMESPACE, stsName).get(0).getMetadata();

        kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(meta.getName())
            .evict(new EvictionBuilder()
                .withMetadata(meta)
                .withDeleteOptions(new DeleteOptionsBuilder()
                    .withGracePeriodSeconds(0L)
                    .build())
                .build());

        LOGGER.info("Checking that pod annotations will contain \"{}: true\"", MANUAL_RU_ANNO);

        StUtils.waitForAnnotationToAppear(Constants.NAMESPACE, meta.getName(), MANUAL_RU_ANNO);

        Map<String, String> annotations = kubeClient().namespace(Constants.NAMESPACE).getPod(meta.getName()).getMetadata().getAnnotations();
        assertEquals("true", annotations.get(MANUAL_RU_ANNO));
    }

    @Test
    void testEvictionRequestOnRandomPod() {
        final String stsName = "my-cluster-bar";
        final Map<String, String> labels = Map.of(
                "app", stsName,
                "strimzi.io/kind", "Kafka"
        );
        LOGGER.info("Creating dummy pod.");
        createStatefulSetAndPDBWithWait(stsName, labels);

        LOGGER.info("Creating eviction request to the pod");
        ObjectMeta meta = kubeClient().listPodsByPrefixInName(Constants.NAMESPACE, stsName).get(0).getMetadata();

        kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(meta.getName())
            .evict(new EvictionBuilder()
                .withMetadata(meta)
                .withDeleteOptions(new DeleteOptionsBuilder()
                    .withGracePeriodSeconds(0L)
                    .build())
                .build());
        LOGGER.info("Checking that pod annotations will not contain \"{}\"", MANUAL_RU_ANNO);
        StUtils.waitForAnnotationToNotAppear(Constants.NAMESPACE, meta.getName(), MANUAL_RU_ANNO);
    }

    void createStatefulSetAndPDBWithWait(String stsName, Map<String, String> labels) {
        StatefulSet statefulSet = new StatefulSetBuilder()
            .withNewMetadata()
                .withName(stsName)
                .withNamespace(Constants.NAMESPACE)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .addToMatchLabels(labels)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels(labels)
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
