package com.bliss.common.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

@Provider
public class RequestIds implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String HEADER = "X-Request-ID";
    private static final String STARTED = RequestIds.class.getName() + ".started";
    private static final Logger LOG = Logger.getLogger("com.bliss.http.requests");
    private static final Set<String> METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    @Context ResourceInfo resource;

    @Override
    public void filter(ContainerRequestContext request) {
        // Generate our own ID so untrusted values cannot contaminate diagnostic logs.
        request.setProperty(HEADER, UUID.randomUUID().toString());
        request.setProperty(STARTED, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle(HEADER, request.getProperty(HEADER));
        if (request.getProperty(HEADER) instanceof String id
                && request.getProperty(STARTED) instanceof Long started) {
            // Operation names come from application code, never URLs or submitted values.
            String operation =
                    resource.getResourceClass() == null || resource.getResourceMethod() == null
                            ? "unmatched"
                            : resource.getResourceClass().getSimpleName()
                                    + "."
                                    + resource.getResourceMethod().getName();
            LOG.infof(
                    "event=rest_response requestId=%s method=%s operation=%s status=%d durationMs=%d",
                    id,
                    METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER",
                    operation,
                    response.getStatus(),
                    Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        }
    }
}
