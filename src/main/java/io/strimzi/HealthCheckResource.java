package io.strimzi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

@Path("/health")
public class HealthCheckResource {
    private static final Logger LOG = Logger.getLogger(HealthCheckResource.class);
    private static final String STATUS = "{\"status\":\"RUNNING\"}";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String hello() {
        LOG.trace("Health check request");
        return STATUS;
    }
}
