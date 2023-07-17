/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.strimzi.utils.Constants;
import io.strimzi.utils.StUtils;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DrainCleanerST extends AbstractST {

    private static final Logger LOGGER = LogManager.getLogger(DrainCleanerST.class);

    private static final String MANUAL_RU_ANNO = "strimzi.io/manual-rolling-update";

    @Test
    void testEvictionRequestOnKafkaPod() {
        final String stsName = "my-cluster-kafka";
        final Map<String, String> labels = Map.of(
                "app", stsName,
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );

        LOGGER.info("Creating dummy pod.");
        createStatefulSetAndPDBWithWait(stsName, labels);

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
        final Map<String, String> labels = Map.of(
                "app", stsName,
                "strimzi.io/kind", "Kafka"
        );

        LOGGER.info("Creating dummy pod.");
        createStatefulSetAndPDBWithWait(stsName, labels);

        LOGGER.info("Creating eviction request to the pod");
        String podName = kubeClient().listPodsByPrefixInName(Constants.NAMESPACE, stsName).get(0).getMetadata().getName();
        kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(podName).evict();

        LOGGER.info("Checking that pod annotations will not contain \"{}\"", MANUAL_RU_ANNO);
        StUtils.waitForAnnotationToNotAppear(Constants.NAMESPACE, podName, MANUAL_RU_ANNO);
    }

    @Test
    void testReloadCertificate() {
        Map<String, String> drainCleanerSnapshot = StUtils.podSnapshot(Constants.NAMESPACE, StUtils.DRAIN_CLEANER_LABEL_SELECTOR);

        String secretName = "strimzi-drain-cleaner";
        Secret oldSecret = kubeClient(Constants.NAMESPACE).getClient().secrets().withName(secretName).get();

        InputStream caCertStream = getClass().getClassLoader().getResourceAsStream("st-certs/ca.crt");
        InputStream caKeyStream = getClass().getClassLoader().getResourceAsStream("st-certs/ca.key");

        final Map<String, String> newSecretData = new HashMap<>();
        newSecretData.put("tls.crt", Base64.getEncoder().encodeToString(StUtils.readResource(caCertStream).getBytes()));
        newSecretData.put("tls.key", Base64.getEncoder().encodeToString(StUtils.readResource(caKeyStream).getBytes()));

        Secret newSecret = new SecretBuilder(oldSecret)
            .withData(newSecretData)
            .build();

        kubeClient().getClient().secrets().resource(newSecret).update();

        StUtils.waitForSecretReady(Constants.NAMESPACE, secretName);
        Secret currentSecret = kubeClient(Constants.NAMESPACE).getClient().secrets().withName(secretName).get();
        assertEquals(currentSecret.getData().get("tls.crt"), newSecretData.get("tls.crt"));
        assertNotEquals(currentSecret.getData().get("tls.crt"), oldSecret.getData().get("tls.crt"));

        StUtils.waitTillDrainCleanerHasRolledAndPodsReady(Constants.NAMESPACE, 1, drainCleanerSnapshot);
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
                        .addToAnnotations("dummy-annotation", "some-value")
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

