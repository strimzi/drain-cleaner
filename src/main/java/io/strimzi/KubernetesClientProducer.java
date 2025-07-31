/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * Custom producer for KubernetesClient that allows client runtime
 * configuration.
 */
@ApplicationScoped
public final class KubernetesClientProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesClientProducer.class);

    /**
     * Quarkus Kubernetes Client 'connection timeout' configuration property.
     */
    @ConfigProperty(name = "quarkus.kubernetes-client.connection-timeout")
    Optional<Integer> connectionTimeout = Optional.empty();

    /**
     * Quarkus Kubernetes Client 'request timeout' configuration property.
     */
    @ConfigProperty(name = "quarkus.kubernetes-client.request-timeout")
    Optional<Integer> requestTimeout = Optional.empty();

    /**
     * Creates a KubernetesClient with custom configurations.
     *
     * @return configured KubernetesClient instance
     */
    @Produces
    @Singleton
    public KubernetesClient kubernetesClient() {
        // Start with the default auto-configured settings
        ConfigBuilder configBuilder = new ConfigBuilder();
        
        // Override the connection timeout if set.
        connectionTimeout.ifPresent(value -> {
            configBuilder.withConnectionTimeout(value);
            LOG.info("Setting Kubernetes client property 'quarkus.kubernetes-client.connection-timeout' to '{}' ms", value);
        });

        // Override the request timeout if set.
        requestTimeout.ifPresent(value -> {
            configBuilder.withRequestTimeout(value);
            LOG.info("Setting Kubernetes client property 'quarkus.kubernetes-client.request-timeout' to '{}' ms", value);
        });

        Config kubernetesConfig = configBuilder.build();
        return new KubernetesClientBuilder().withConfig(kubernetesConfig).build();
    }
}
