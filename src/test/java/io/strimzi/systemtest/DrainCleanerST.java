/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.fabric8.kubernetes.api.model.DeleteOptionsBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PreconditionsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.Eviction;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.EvictionBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.strimzi.utils.Constants;
import io.strimzi.utils.SecretUtils;
import io.strimzi.utils.StUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static io.strimzi.utils.k8s.KubeClusterResource.kubeClient;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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

        KubernetesClientException ex = assertThrows(KubernetesClientException.class, () -> kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(podName).evict());
        assertThat(ex.getCode(), is(500));
        assertThat(ex.getMessage(), containsString("The pod will be rolled by the Strimzi Cluster Operator."));

        LOGGER.info("Checking that pod annotations will contain \"{}: true\"", MANUAL_RU_ANNO);

        StUtils.waitForAnnotationToAppear(Constants.NAMESPACE, podName, MANUAL_RU_ANNO);

        Map<String, String> annotations = kubeClient().namespace(Constants.NAMESPACE).getPod(podName).getMetadata().getAnnotations();
        assertEquals("true", annotations.get(MANUAL_RU_ANNO));
    }

    @Test
    void testEvictionRequestOnKafkaPodWithCorrectUUID() {
        final String stsName = "my-cluster-kafka";
        final Map<String, String> labels = Map.of(
                "app", stsName,
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );

        LOGGER.info("Creating dummy pod.");
        createStatefulSetAndPDBWithWait(stsName, labels);

        LOGGER.info("Creating eviction request to the pod");
        Pod pod = kubeClient().listPodsByPrefixInName(Constants.NAMESPACE, stsName).get(0);
        Eviction eviction = new EvictionBuilder()
                .withNewMetadata()
                    .withName(pod.getMetadata().getName())
                    .withNamespace(pod.getMetadata().getNamespace())
                .endMetadata()
                .withDeleteOptions(new DeleteOptionsBuilder().withPreconditions(new PreconditionsBuilder().withUid(pod.getMetadata().getUid()).build()).build())
                .build();

        KubernetesClientException ex = assertThrows(KubernetesClientException.class, () -> kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(pod.getMetadata().getName()).evict(eviction));
        assertThat(ex.getCode(), is(500));
        assertThat(ex.getMessage(), containsString("The pod will be rolled by the Strimzi Cluster Operator."));

        LOGGER.info("Checking that pod annotations will contain \"{}: true\"", MANUAL_RU_ANNO);

        StUtils.waitForAnnotationToAppear(Constants.NAMESPACE, pod.getMetadata().getName(), MANUAL_RU_ANNO);

        Map<String, String> annotations = kubeClient().namespace(Constants.NAMESPACE).getPod(pod.getMetadata().getName()).getMetadata().getAnnotations();
        assertEquals("true", annotations.get(MANUAL_RU_ANNO));
    }

    @Test
    void testEvictionRequestOnKafkaPodWithWrongUUID() {
        final String stsName = "my-cluster-kafka";
        final Map<String, String> labels = Map.of(
                "app", stsName,
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );

        LOGGER.info("Creating dummy pod.");
        createStatefulSetAndPDBWithWait(stsName, labels);

        LOGGER.info("Creating eviction request to the pod");
        Pod pod = kubeClient().listPodsByPrefixInName(Constants.NAMESPACE, stsName).get(0);
        Eviction eviction = new EvictionBuilder()
                .withNewMetadata()
                    .withName(pod.getMetadata().getName())
                    .withNamespace(pod.getMetadata().getNamespace())
                .endMetadata()
                .withDeleteOptions(new DeleteOptionsBuilder().withPreconditions(new PreconditionsBuilder().withUid("21f2e0da-b547-4c31-a24f-230d2edb17e5").build()).build()) // Random UID
                .build();

        boolean evicted = kubeClient().getClient().pods().inNamespace(Constants.NAMESPACE).withName(pod.getMetadata().getName()).evict(eviction);
        assertThat(evicted, is(false));

        LOGGER.info("Checking that pod annotation {} will not be set", MANUAL_RU_ANNO);

        StUtils.waitForAnnotationToNotAppear(Constants.NAMESPACE, pod.getMetadata().getName(), MANUAL_RU_ANNO);
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
        Secret oldSecret = kubeClient().getClient().secrets().inNamespace(Constants.NAMESPACE).withName(secretName).get();

        Secret newSecret = SecretUtils.createDrainCleanerSecret().build();

        kubeClient().getClient().secrets().resource(newSecret).update();

        StUtils.waitForSecretReady(Constants.NAMESPACE, secretName);
        Secret currentSecret = kubeClient().getClient().secrets().inNamespace(Constants.NAMESPACE).withName(secretName).get();
        assertEquals(currentSecret.getData().get("tls.crt"), newSecret.getData().get("tls.crt"));
        assertNotEquals(currentSecret.getData().get("tls.crt"), oldSecret.getData().get("tls.crt"));

        StUtils.waitTillDrainCleanerHasRolledAndPodsReady(Constants.NAMESPACE, StUtils.DRAIN_CLEANER_LABEL_SELECTOR, 1, drainCleanerSnapshot);
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

        if (kubeClient().getClient().apps().statefulSets().inNamespace(Constants.NAMESPACE).resource(statefulSet).get() != null)  {
            LOGGER.info("Deleting previous StatefulSet");
            kubeClient().getClient().apps().statefulSets().inNamespace(Constants.NAMESPACE).resource(statefulSet).delete();
        }

        StUtils.createStatefulSetWithWait(statefulSet);

        if (kubeClient().getClient().policy().v1().podDisruptionBudget().inNamespace(Constants.NAMESPACE).resource(pdb).get() != null)  {
            LOGGER.info("Deleting previous Pod Disruption Budget");
            kubeClient().getClient().policy().v1().podDisruptionBudget().inNamespace(Constants.NAMESPACE).resource(pdb).delete();
        }

        StUtils.createPodDisruptionBudgetWithWait(pdb);
    }
}

