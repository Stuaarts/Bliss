package com.bliss.common.api;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class ApiErrors implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(ApiErrors.class);
    @Context ContainerRequestContext request;

    public record Problem(String type, String title, int status, String detail, String requestId) {}

    @Override
    public Response toResponse(Exception exception) {
        int status =
                exception instanceof ConstraintViolationException
                        ? 400
                        : exception instanceof OptimisticLockException
                                ? 409
                                : exception instanceof WebApplicationException web
                                        ? web.getResponse().getStatus()
                                        : 500;
        return problem(exception, status);
    }

    Response problem(Exception exception, int status) {
        String requestId = String.valueOf(request.getProperty(RequestIds.HEADER));
        String detail =
                switch (status) {
                    case 400 -> "Check request parameters and try again.";
                    case 401 -> "Authentication is required.";
                    case 403 -> "You do not have access to this resource.";
                    case 404 -> "The requested resource was not found.";
                    case 405 -> "This action is not supported.";
                    case 409 -> "This record changed. Refresh and try again.";
                    default -> "The request could not be completed.";
                };
        if (status >= 500) LOG.errorf(exception, "API failure requestId=%s", requestId);
        var statusType = Response.Status.fromStatusCode(status);
        String title = statusType == null ? "Request failed" : statusType.getReasonPhrase();
        return Response.status(status)
                .type("application/problem+json")
                .entity(new Problem("about:blank", title, status, detail, requestId))
                .build();
    }
}
