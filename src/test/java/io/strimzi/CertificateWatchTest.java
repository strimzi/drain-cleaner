/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CertificateWatchTest {
    private final static String NAMESPACE = "my-namespace";
    private final static String POD_NAME = "my-pod";
    private final static String SECRET_NAME = "my-secret";
    private final static List<String> SECRET_KEYS = List.of("tls.crt", "tls.key");
    private final static Secret INITIAL_SECRET = new SecretBuilder()
            .withNewMetadata()
                .withName(SECRET_NAME)
                .withNamespace(NAMESPACE)
            .endMetadata()
            .withData(Map.of("tls.crt", "YXZmYw==", "tls.key", "MTg3NA=="))
            .build();

    KubernetesClient client;

    MixedOperation<Pod, PodList, PodResource> pods;
    NonNamespaceOperation<Pod, PodList, PodResource> podsInNamespace;
    PodResource podResource;

    MixedOperation<Secret, SecretList, Resource<Secret>> secrets;
    NonNamespaceOperation<Secret, SecretList, Resource<Secret>> secretsInNamespace;
    Resource<Secret> secretResource;
    SharedIndexInformer<Secret> secretInformer;
    ArgumentCaptor<ResourceEventHandler<Secret>> secretInformerHandlerCaptor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        client = mock(KubernetesClient.class);

        // Mock pod for deletion
        pods = mock(MixedOperation.class);
        podsInNamespace = mock(NonNamespaceOperation.class);
        podResource = mock(PodResource.class);

        when(podsInNamespace.withName(eq(POD_NAME))).thenReturn(podResource);
        when(pods.inNamespace(eq(NAMESPACE))).thenReturn(podsInNamespace);
        when(client.pods()).thenReturn(pods);

        // Mock Secret
        secrets = mock(MixedOperation.class);
        secretsInNamespace = mock(NonNamespaceOperation.class);
        secretResource = mock(Resource.class);

        when(secretResource.get()).thenReturn(INITIAL_SECRET);
        when(secretsInNamespace.withName(eq(SECRET_NAME))).thenReturn(secretResource);
        when(secrets.inNamespace(eq(NAMESPACE))).thenReturn(secretsInNamespace);
        when(client.secrets()).thenReturn(secrets);

        // Mock the informer
        secretInformer = mock(SharedIndexInformer.class);
        secretInformerHandlerCaptor = ArgumentCaptor.forClass(ResourceEventHandler.class);

        when(secretResource.inform(secretInformerHandlerCaptor.capture())).thenReturn(secretInformer);
    }

    @Test
    public void testValidationWhenDisabled()    {
        CertificateWatch watch = new CertificateWatch(null, false, null, null, null, null);
        watch.start();
        watch.stop();
    }

    @Test
    public void testValidationFailureWhenEnabled()    {
        RuntimeException e = assertThrows(RuntimeException.class, () -> new CertificateWatch(null, true, null, null, null, null));
        assertThat(e.getMessage(), is("Certificate watch is enabled but missing one or more required options: [strimzi.certificate.watch.namespace, strimzi.certificate.watch.pod.name, strimzi.certificate.watch.secret.name, strimzi.certificate.watch.secret.keys]"));
    }

    @Test
    public void testValidationWhenEnabled()    {
        CertificateWatch watch = new CertificateWatch(client, true, NAMESPACE, POD_NAME, SECRET_NAME, SECRET_KEYS);
        watch.start();
        watch.stop();
    }

    @Test
    public void testInitialization()    {
        CertificateWatch watch = new CertificateWatch(client, true, NAMESPACE, POD_NAME, SECRET_NAME, SECRET_KEYS);
        watch.start();
        watch.stop();

        assertThat(watch.previousValues, is("YXZmYw==MTg3NA=="));
        verify(secretResource, times(1)).get();
        verify(secretInformer, times(1)).start();
        verify(secretInformer, times(1)).stop();
        assertThat(secretInformerHandlerCaptor.getValue(), is(notNullValue()));
    }

    @Test
    public void testPodDeletionWhenSecretChanged() throws InterruptedException {
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);

        CertificateWatch watch = new MockedCertificateWatch(client, true, NAMESPACE, POD_NAME, SECRET_NAME, SECRET_KEYS, checked, deleted);
        watch.start();

        Secret modifiedSecret = new SecretBuilder(INITIAL_SECRET)
                .withData(Map.of("tls.crt", "c2lnbWE=", "tls.key", "MTkxOQ=="))
                .build();

        secretInformerHandlerCaptor.getValue().onUpdate(INITIAL_SECRET, modifiedSecret);

        assertThat(checked.await(1, TimeUnit.SECONDS), is(true));
        assertThat(deleted.await(1, TimeUnit.SECONDS), is(true));

        verify(podResource, times(1)).delete();
    }

    @Test
    public void testPodDeletionWhenSecretIsAdded() throws InterruptedException {
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);

        CertificateWatch watch = new MockedCertificateWatch(client, true, NAMESPACE, POD_NAME, SECRET_NAME, SECRET_KEYS, checked, deleted);
        watch.start();

        Secret modifiedSecret = new SecretBuilder(INITIAL_SECRET)
                .withData(Map.of("tls.crt", "c2lnbWE=", "tls.key", "MTkxOQ=="))
                .build();

        secretInformerHandlerCaptor.getValue().onAdd(modifiedSecret);

        assertThat(checked.await(1, TimeUnit.SECONDS), is(true));
        assertThat(deleted.await(1, TimeUnit.SECONDS), is(true));

        verify(podResource, times(1)).delete();
    }

    @Test
    public void testNoPodDeletionWhenNewFieldIsAdded() throws InterruptedException {
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);

        CertificateWatch watch = new MockedCertificateWatch(client, true, NAMESPACE, POD_NAME, SECRET_NAME, SECRET_KEYS, checked, deleted);
        watch.start();

        Secret modifiedSecret = new SecretBuilder(INITIAL_SECRET)
                .addToData("other.file", "YmlybWluZ2hhbQ==")
                .build();

        secretInformerHandlerCaptor.getValue().onUpdate(INITIAL_SECRET, modifiedSecret);

        assertThat(checked.await(1, TimeUnit.SECONDS), is(true));

        verify(podResource, never()).delete();
    }

    @Test
    public void testNoPodDeletionWhenNothingChanges() throws InterruptedException {
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);

        CertificateWatch watch = new MockedCertificateWatch(client, true, NAMESPACE, POD_NAME, SECRET_NAME, SECRET_KEYS, checked, deleted);
        watch.start();

        secretInformerHandlerCaptor.getValue().onUpdate(INITIAL_SECRET, INITIAL_SECRET);

        assertThat(checked.await(1, TimeUnit.SECONDS), is(true));

        verify(podResource, never()).delete();
    }

    /**
     * This class is used to inject countdown latches to help with the tests
     */
    static class MockedCertificateWatch extends CertificateWatch   {
        private final CountDownLatch podDeletionLatch;
        private final CountDownLatch checkForChangesLatch;

        public MockedCertificateWatch(KubernetesClient client, boolean enabled, String namespace, String podName, String secretName, List<String> secretKeys, CountDownLatch checkForChangesLatch, CountDownLatch podDeletionLatch) {
            super(client, enabled, namespace, podName, secretName, secretKeys);
            this.checkForChangesLatch = checkForChangesLatch;
            this.podDeletionLatch = podDeletionLatch;
        }

        @Override
        void checkForChanges(Secret secret) {
            super.checkForChanges(secret);
            checkForChangesLatch.countDown();
        }

        @Override
        void restartDrainCleaner()  {
            super.restartDrainCleaner();
            podDeletionLatch.countDown();
        }
    }
}
