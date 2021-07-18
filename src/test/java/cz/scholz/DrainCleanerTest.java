package cz.scholz;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.api.model.policy.EvictionBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class DrainCleanerTest {
    KubernetesClient client;
    MixedOperation<Pod, PodList, PodResource<Pod>> pods;
    NonNamespaceOperation<Pod, PodList, PodResource<Pod>> inNamespace;
    PodResource<Pod> podResource;

    @BeforeEach
    public void setup() {
        client = mock(KubernetesClient.class);
        pods = mock(MixedOperation.class);
        inNamespace = mock(NonNamespaceOperation.class);
        podResource = mock(PodResource.class);

        when(inNamespace.withName(any())).thenReturn(podResource);
        when(pods.inNamespace(any())).thenReturn(inNamespace);
        when(client.pods()).thenReturn(pods);
    }

    @Test
    public void testEviction() {
        String podName = "my-cluster-kafka-1";
        when(podResource.get()).thenReturn(mockedPod(podName, true, false));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, Pattern.compile(".+(-kafka-|-zookeeper-)[0-9]+"));
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(podName, false));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, times(1)).patch(any());
        assertThat(podCaptor.getValue().getMetadata().getAnnotations().get("strimzi.io/manual-rolling-update"), is("true"));
    }

    @Test
    public void testWrongName() {
        String podName = "my-cluster-connect-1";
        when(podResource.get()).thenReturn(mockedPod(podName, true, false));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, Pattern.compile(".+(-kafka-|-zookeeper-)[0-9]+"));
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(podName, false));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, never()).get();
        verify(podResource, never()).patch(any());
    }

    @Test
    public void testDryRun() {
        String podName = "my-cluster-connect-1";
        ValidatingWebhook webhook = new ValidatingWebhook(client, Pattern.compile(".+(-kafka-|-zookeeper-)[0-9]+"));
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(podName, true));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, never()).get();
        verify(podResource, never()).patch(any());
    }

    @Test
    public void testNoKindLabel() {
        String podName = "my-cluster-kafka-1";
        when(podResource.get()).thenReturn(mockedPod(podName, false, false));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, Pattern.compile(".+(-kafka-|-zookeeper-)[0-9]+"));
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(podName, false));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch(any());
    }

    @Test
    public void testPodDoesNotExist() {
        String podName = "my-cluster-kafka-1";
        when(podResource.get()).thenReturn(null);

        ValidatingWebhook webhook = new ValidatingWebhook(client, Pattern.compile(".+(-kafka-|-zookeeper-)[0-9]+"));
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(podName, false));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch(any());
    }

    @Test
    public void testAlreadyHasRuLabel() {
        String podName = "my-cluster-kafka-1";
        when(podResource.get()).thenReturn(mockedPod(podName, true, true));
        ArgumentCaptor<Pod> podCaptor = ArgumentCaptor.forClass(Pod.class);
        when(podResource.patch(podCaptor.capture())).thenReturn(new Pod());

        ValidatingWebhook webhook = new ValidatingWebhook(client, Pattern.compile(".+(-kafka-|-zookeeper-)[0-9]+"));
        AdmissionReview reviewResponse = webhook.webhook(reviewRequest(podName, false));

        assertThat(reviewResponse.getResponse().getUid(), is("SOME-UUID"));
        assertThat(reviewResponse.getResponse().getAllowed(), is(true));
        verify(podResource, times(1)).get();
        verify(podResource, never()).patch(any());
    }

    private Pod mockedPod(String podName, boolean kindLabel, boolean ruAnno) {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withLabels(Collections.emptyMap())
                    .withAnnotations(Collections.emptyMap())
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .build();

        if (kindLabel)  {
            pod.getMetadata().getLabels().put("strimzi.io/kind", "Kafka");
        }

        if (ruAnno)  {
            pod.getMetadata().getAnnotations().put("strimzi.io/manual-rolling-update", "true");
        }

        return pod;
    }

    private AdmissionReview reviewRequest(String podName, boolean dryRun)   {
        AdmissionRequest admissionRequest = new AdmissionRequest();
        admissionRequest.setObject(new EvictionBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace("my-namespace")
                .endMetadata()
                .build());
        admissionRequest.setDryRun(dryRun);
        admissionRequest.setUid("SOME-UUID");

        return new AdmissionReviewBuilder()
                .withRequest(admissionRequest)
                .build();
    }
}
