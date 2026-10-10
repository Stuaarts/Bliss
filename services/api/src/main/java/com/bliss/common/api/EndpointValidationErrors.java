package com.bliss.common.api;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.inject.Inject;
import jakarta.validation.ElementKind;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.stream.StreamSupport;

/** Keep endpoint validation correlated without exposing violation paths or values. */
@Provider
public class EndpointValidationErrors
        implements ExceptionMapper<ResteasyReactiveViolationException> {
    @Inject ApiErrors errors;

    @Override
    public Response toResponse(ResteasyReactiveViolationException exception) {
        // A broken endpoint return contract is an internal failure, not bad input.
        boolean invalidReturn =
                exception.getConstraintViolations().stream()
                        .flatMap(
                                violation ->
                                        StreamSupport.stream(
                                                violation.getPropertyPath().spliterator(), false))
                        .anyMatch(node -> node.getKind() == ElementKind.RETURN_VALUE);
        return errors.problem(exception, invalidReturn ? 500 : 400);
    }
}
