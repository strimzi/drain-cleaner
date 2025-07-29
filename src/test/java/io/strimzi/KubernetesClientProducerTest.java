/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for the custom KubernetesClient producer to ensure it creates a client
 * with proper configuration.
 */
public class KubernetesClientProducerTest {

    private KubernetesClientProducer producer;

    @BeforeEach
    public void setup() {
        producer = new KubernetesClientProducer();
    }

    /**
     * Test that the KubernetesClient is properly created with default configuration.
     */
    @Test
    public void testKubernetesClientCreationWithDefaults() {
        // Create client with default configuration (no custom timeouts)
        KubernetesClient client = producer.kubernetesClient();
        
        // Verify the client was created
        assertNotNull(client, "KubernetesClient should be created");
        assertNotNull(client.getConfiguration(), "Client configuration should be accessible");
    }

    /**
     * Test that custom timeouts are properly applied to the KubernetesClient
     * when configured by the user.
     */
    @Test
    public void testKubernetesClientCreationWithCustomTimeouts() {
        // Simulate user configuration by setting the timeout values directly
        producer.connectionTimeout = Optional.of(30000);
        producer.requestTimeout = Optional.of(60000);

        // Create client with custom configuration
        KubernetesClient client = producer.kubernetesClient();

        // Verify the client was created and configured correctly
        assertNotNull(client, "KubernetesClient should be created");
        assertNotNull(client.getConfiguration(), "Client configuration should be accessible");

        int connectionTimeout = client.getConfiguration().getConnectionTimeout();
        int requestTimeout = client.getConfiguration().getRequestTimeout();

        assertEquals(30000, connectionTimeout, "Connection timeout should be set to 30000ms as configured by user");
        assertEquals(60000, requestTimeout, "Request timeout should be set to 60000ms as configured by user");
    }
}
