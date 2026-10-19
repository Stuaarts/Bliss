package com.bliss.common.api;

import com.bliss.operations.WriteAdmission;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;
import org.eclipse.microprofile.health.Readiness;

/** Match REST resources after correlation is generated, before parsing or invoking a write. */
@Provider
@Priority(Priorities.USER + 100)
public class WriteAdmissionFilter implements ContainerRequestFilter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    @Inject @Readiness WriteAdmission admission;

    @Override
    public void filter(ContainerRequestContext request) {
        if (!admission.paused() || READ_METHODS.contains(request.getMethod())) return;
        String requestId = (String) request.getProperty(RequestIds.HEADER);
        request.abortWith(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .type("application/problem+json")
                        .header("Cache-Control", "no-store")
                        .entity(
                                new ApiErrors.Problem(
                                        "about:blank",
                                        "Service Unavailable",
                                        503,
                                        "API writes are temporarily paused.",
                                        requestId))
                        .build());
    }
}
