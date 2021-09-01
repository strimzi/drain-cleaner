/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
}
