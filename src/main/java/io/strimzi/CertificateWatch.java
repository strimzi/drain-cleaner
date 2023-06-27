/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Quarkus loads the TLS certificate at startup but does not reload it when it changes. So if the Drain Cleaner runs for
 * too long, it can happen that the certificate expires and Kubernetes cannot call the webhook. To prevent this, the
 * CertificateWatch is watching for changes to the Kubernetes Secret with the TLS certificates. If any changes are
 * detected, it will delete itself through the Kubernetes API (delete the Pod it runs in). It is by default disabled
 * in development. The configuration options {@code strimzi.certificate.watch.enabled} and
 * {@code strimzi.certificate.watch.*} options can be used to customize the default settings.
 */
@ApplicationScoped
public class CertificateWatch {
    private static final Logger LOG = LoggerFactory.getLogger(CertificateWatch.class);

    @Inject
    KubernetesClient client;

    private final boolean enabled;
    private final String namespace;
    private final String podName;
    private final String secretName;
    private final List<String> secretKeys;

    private SharedIndexInformer<Secret> secretInformer;
    /* test */ String previousValues;

    /**
     * Constructs the certificate watch. If watching for the certificate changes is enabled, it will also validate the
     * configuration. This is the default constructor used in production which gets the values from quarkus configuration.
     */
    @SuppressWarnings("unused")
    public CertificateWatch() {
        this(ConfigProvider.getConfig().getOptionalValue("strimzi.certificate.watch.enabled", Boolean.class).orElse(false),
                ConfigProvider.getConfig().getOptionalValue("strimzi.certificate.watch.namespace", String.class).orElse(null),
                ConfigProvider.getConfig().getOptionalValue("strimzi.certificate.watch.pod.name", String.class).orElse(null),
                ConfigProvider.getConfig().getOptionalValue("strimzi.certificate.watch.secret.name", String.class).orElse(null),
                ConfigProvider.getConfig().getOptionalValues("strimzi.certificate.watch.secret.keys", String.class).orElse(null));
    }

    /**
     * Constructor used by tests to pass mocked values
     *
     * @param client        Kubernetes client
     * @param enabled       Enables / disables the certificate watch
     * @param namespace     Drain Cleaner namespace
     * @param podName       Drain Cleaner podName
     * @param secretName    Name of the certificate Secret
     * @param secretKeys    Keys under which the certificates are stored in the secret
     */
    /* test */ CertificateWatch(KubernetesClient client, boolean enabled, String namespace, String podName, String secretName, List<String> secretKeys)  {
        this(enabled, namespace, podName, secretName, secretKeys);
        this.client = client;
    }

    /**
     * Private constructor used to set the right values which is called from production and from tests.
     *
     * @param enabled       Enables / disables the certificate watch
     * @param namespace     Drain Cleaner namespace
     * @param podName       Drain Cleaner podName
     * @param secretName    Name of the certificate Secret
     * @param secretKeys    Keys under which the certificates are stored in the secret
     */
    private CertificateWatch(boolean enabled, String namespace, String podName, String secretName, List<String> secretKeys)  {
        this.enabled = enabled;
        this.namespace = namespace;
        this.podName = podName;
        this.secretName = secretName;
        this.secretKeys = secretKeys;

        if (this.enabled) {
            // Validate configuration and throw exception if something is missing
            validateWatchConfiguration();
        } else {
            LOG.info("Certificate watch is disabled");
        }
    }

    /**
     * Starts the watcher when Quarkus is starting
     *
     * @param ev    Startup event
     */
    void onStart(@Observes StartupEvent ev) {
        start();
    }

    /**
     * Stops the watch when Quarkus is stopping
     *
     * @param ev    Shutdown event
     */
    void onStop(@Observes ShutdownEvent ev) {
        stop();
    }

    /**
     * Starts the watcher (if enabled). It will first get the secret to get the initial data. Afterwards it sets up
     * and starts an informer to be informed about any changes to it.
     */
    /* test */ void start() {
        if (enabled) {
            LOG.info("Starting the certificate watch");
            initialize();
            setupInformer();
            secretInformer.start();
        }
    }

    /**
     * Stops the informer
     */
    /* test */ void stop() {
        if (enabled) {
            LOG.info("Stopping the certificate watch");
            secretInformer.stop();
        }
    }

    /**
     * Validates the Certificate Watch configuration and throws exception if any options are missing.
     */
    private void validateWatchConfiguration()   {
        List<String> missingOptions = new ArrayList<>();

        if (namespace == null)  {
            missingOptions.add("strimzi.certificate.watch.namespace");
        }

        if (podName == null)  {
            missingOptions.add("strimzi.certificate.watch.pod.name");
        }

        if (secretName == null)  {
            missingOptions.add("strimzi.certificate.watch.secret.name");
        }

        if (secretKeys == null || secretKeys.isEmpty())  {
            missingOptions.add("strimzi.certificate.watch.secret.keys");
        }

        if (!missingOptions.isEmpty())   {
            LOG.error("Certificate watch is enabled but missing one or more required options: {}", missingOptions);
            throw new RuntimeException("Certificate watch is enabled but missing one or more required options: " + missingOptions);
        }
    }

    /**
     * Gets the certificate secret and loads the initial values of the selected fields from it. The values are expected
     * to be certificates -> small enough to just store them as a String.
     */
    private void initialize()   {
        Secret watchedSecret = client.secrets().inNamespace(namespace).withName(secretName).get();

        if (watchedSecret == null)  {
            LOG.error("Certificate Secret {} which should be watched was not found", secretName);
            throw new RuntimeException("Certificate Secret " + secretName + " which should be watched was not found");
        } else {
            LOG.info("Getting initial values from the secret");
            previousValues = getSecretData(watchedSecret);
        }
    }

    /**
     * Sets up the Secret informer with the event handler which is triggered when the secret is added or changes. When
     * the secret is deleted, we do not do anything -> in such case, the new Pod would not start anyway. We expect the
     * secret to be created again later which is when we might restart if the certificates differ.
     */
    private void setupInformer()    {
        secretInformer = client.secrets().inNamespace(namespace).withName(secretName).inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Secret secret) {
                LOG.info("Secret {} was added and will be checked for changes", secret.getMetadata().getName());
                checkForChanges(secret);
            }

            @Override
            public void onUpdate(Secret oldSecret, Secret newSecret) {
                LOG.info("Secret {} was modified and will be checked for changes", newSecret.getMetadata().getName());
                checkForChanges(newSecret);
            }

            @Override
            public void onDelete(Secret secret, boolean deletedFinalStateUnknown) {
                LOG.info("Secret {} was deleted -> nothing to do", secret.getMetadata().getName());
            }
        });
    }

    /**
     * Checks the Secret for changes. It extracts the selected keys from it and compares it to the previously known
     * value. If they differ, it triggers the restart of the Drain Cleaner.
     *
     * @param secret    Secret with the values which should be compared with the known certificates
     */
    /* test */ void checkForChanges(Secret secret) {
        if (secret == null
                || !previousValues.equals(getSecretData(secret))) {
            LOG.info("The watched fields {} changed and Drain Cleaner restart will be triggered", secretKeys);

            // The pod is deleted asynchronously to not block the event handler from the secretInformer
            // Doing it synchronously was causing Fabric8 exceptions since it was blocking the thread while the informer was
            // shutting down => because it is deleting itself
            CompletableFuture.supplyAsync(() -> {
                restartDrainCleaner();
                return (Void) null;
            });
        } else {
            LOG.info("No change to watched fields ({}) detected", secretKeys);
        }
    }

    /**
     * Restarts this Drain Cleaner Pod by deleting it through the Kubernetes API
     */
    /* test */ void restartDrainCleaner()  {
        LOG.info("Deleting pod {} to restart Drain Cleaner and reload certificates", podName);
        client.pods().inNamespace(namespace).withName(podName).delete();
    }

    /**
     * Extracts the data from the Secret
     *
     * @param secret   Secret from which the data should be extracted
     *
     * @return  The values of selected keys from the Secret as one long String
     */
    private String getSecretData(Secret secret) {
        StringBuilder sb = new StringBuilder();

        if (secret.getData() != null) {
            for (String key : secretKeys) {
                if (secret.getData().containsKey(key)) {
                    sb.append(secret.getData().get(key));
                } else {
                    LOG.warn("Watched key {} is not present in Secret {}", key, secret.getMetadata().getName());
                }
            }
        } else {
            LOG.warn("Watched Secret {} has no data", secret.getMetadata().getName());
        }

        return sb.toString();
    }
}
