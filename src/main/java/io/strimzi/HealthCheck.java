/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.quarkus.vertx.http.ManagementInterface;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

@Path("/health")
public class HealthCheck {
    private static final Logger LOG = Logger.getLogger(HealthCheck.class);
    public static final String RUNNING = "{\"status\":\"RUNNING\"}";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String health() {
        LOG.trace("Health check request");
        return RUNNING;
    }

    void registerManagementHealth(@Observes ManagementInterface management) {
        management.router().get("/health").handler(context -> context.response()
                .putHeader("Content-Type", MediaType.APPLICATION_JSON)
                .end(health()));
    }
}
