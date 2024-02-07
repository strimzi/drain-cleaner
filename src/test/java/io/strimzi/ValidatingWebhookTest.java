/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.EvictionBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.when;

public class ValidatingWebhookTest {
    KubernetesClient client;
    MixedOperation<Pod, PodList, PodResource> pods;
    NonNamespaceOperation<Pod, PodList, PodResource> inNamespace;
    PodResource podResource;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        client = mock(KubernetesClient.class);
        pods = mock(MixedOperation.class);
        inNamespace = mock(NonNamespaceOperation.class);
        podResource = mock(PodResource.class);

        when(inNamespace.withName(any())).thenReturn(podResource);
        when(pods.inNamespace(eq("my-namespace"))).thenReturn(inNamespace);
        when(client.pods()).thenReturn(pods);
    }

    @Test
    public void testEvictionAllowed() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );
        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, false);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testEvictionDenied() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );
        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testV1Beta1Eviction() {
        String podName = "my-cluster-kafka-1";
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );
        AdmissionRequest admissionRequest = new AdmissionRequest();
        admissionRequest.setObject(new io.fabric8.kubernetes.api.model.policy.v1beta1.EvictionBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace("my-namespace")
                .endMetadata()
                .build());
        admissionRequest.setDryRun(false);
        admissionRequest.setUid("SOME-UUID");

        AdmissionReview admissionReview =  new AdmissionReviewBuilder()
                .withRequest(admissionRequest)
                .build();

        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(admissionReview);

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testEvictionOnPodWithoutAnnotations() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );
        Pod mockedPod = mockedPod(false, labels);
        mockedPod.getMetadata().setAnnotations(null);

        when(podResource.get()).thenReturn(mockedPod);
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().size(), is(1));
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testEvictionOnPodWithExistingAnnotations() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );
        Pod mockedPod = mockedPod(false, labels);
        mockedPod.getMetadata().setAnnotations(Map.of("someAnno1", "someValue1", "someAnno2", "someValue2"));

        when(podResource.get()).thenReturn(mockedPod);
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().size(), is(3));
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("someAnno1"), is("someValue1"));
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("someAnno2"), is("someValue2"));
    }

    @Test
    public void testEmptyLabel() {
        final Map<String, String> labels = Collections.emptyMap();

        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testNullLabel() {
        when(podResource.get()).thenReturn(mockedPod(false, null));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, null));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testCorrectLabelWrongValue() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-foo"
        );

        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testCorrectLabelCorrectKafkaValue() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );

        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testCorrectLabelCorrectZooKeeperValue() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );

        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testCorrectKafkaLabelWithDisabledKafkaDraining() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-kafka"
        );

        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, false, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testCorrectZooKeeperLabelWithDisabledZooKeeperDraining() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, false, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testDryRun() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(true, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testKafkaAndZooKeeperFilters() {
        ValidatingWebhook webhook = new ValidatingWebhook(client, false, false, true);
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        // Test it for Kafka
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(true, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());

        // Test it for ZooKeeper
        reviewResponse = webhook.webhook(reviewRequest(true, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(2)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testNoKindLabel() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testPodDoesNotExist() {
        when(podResource.get()).thenReturn(null);
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testAlreadyHasRuLabel() {
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        when(podResource.get()).thenReturn(mockedPod(true, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(false, labels));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch((Pod) any());
    }

    @Test
    public void testEvictionWithoutNamespace() {
        String podName = "my-cluster-kafka-1";
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        AdmissionRequest admissionRequest = new AdmissionRequest();
        admissionRequest.setObject(new EvictionBuilder()
                .withNewMetadata()
                .withName(podName)
                .endMetadata()
                .build());
        admissionRequest.setDryRun(false);
        admissionRequest.setNamespace("my-namespace");
        admissionRequest.setUid("SOME-UUID");

        AdmissionReview request =  new AdmissionReviewBuilder()
                .withRequest(admissionRequest)
                .build();

        when(podResource.get()).thenReturn(mockedPod(false, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(request);

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(false));
        assertThat(reviewResponse.getResponse().getStatus().getCode(), is(500));
        assertThat(reviewResponse.getResponse().getStatus().getMessage(), is("The pod will be rolled by the Strimzi Cluster Operator"));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch((Pod) any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testNoNamespaceAnywhere() {
        String podName = "my-cluster-kafka-1";
        final Map<String, String> labels = Map.of(
                "strimzi.io/kind", "Kafka",
                "strimzi.io/name", "my-cluster-zookeeper"
        );
        AdmissionRequest admissionRequest = new AdmissionRequest();
        admissionRequest.setObject(new EvictionBuilder()
                .withNewMetadata()
                    .withName(podName)
                .endMetadata()
                .build());
        admissionRequest.setDryRun(false);
        admissionRequest.setUid("SOME-UUID");

        AdmissionReview request =  new AdmissionReviewBuilder()
                .withRequest(admissionRequest)
                .build();

        when(podResource.get()).thenReturn(mockedPod(true, labels));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, true, true, true);
        AdmissionReview reviewResponse = webhook.webhook(request);

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, never()).get();
        verify(podResource, never()).patch((Pod) any());
    }

    private Pod mockedPod(boolean ruAnno, Map<String, String> labels) {
        String podName = "my-cluster-kafka-1";
        Pod pod = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withLabels(labels)
                    .withNamespace("my-namespace")
                    .withAnnotations(Collections.emptyMap())
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .build();

        if (ruAnno)  {
            pod.getMetadata().getAnnotations().put("strimzi.io/manual-rolling-update", "true");
        }

        return pod;
    }

    private AdmissionReview reviewRequest(boolean dryRun, Map<String, String> labels)   {
        String podName = "my-cluster-kafka-1";
        AdmissionRequest admissionRequest = new AdmissionRequest();
        admissionRequest.setObject(new EvictionBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace("my-namespace")
                    .withLabels(labels)
                .endMetadata()
                .build());
        admissionRequest.setDryRun(dryRun);
        admissionRequest.setUid("SOME-UUID");

        return new AdmissionReviewBuilder()
                .withRequest(admissionRequest)
                .build();
    }
}
