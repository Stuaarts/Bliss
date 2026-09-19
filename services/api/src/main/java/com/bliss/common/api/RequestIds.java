package com.bliss.common.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;

@Provider
public class RequestIds implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String HEADER = "X-Request-ID";

    @Override
    public void filter(ContainerRequestContext request) {
        // Generate our own ID so untrusted values cannot contaminate diagnostic logs.
        request.setProperty(HEADER, UUID.randomUUID().toString());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle(HEADER, request.getProperty(HEADER));
    }
}
