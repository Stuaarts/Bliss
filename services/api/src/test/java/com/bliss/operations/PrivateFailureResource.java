package com.bliss.operations;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** Test-only failure with private content in both the message and nested cause. */
@Path("/test/operations/private-failure/{input}")
public class PrivateFailureResource {
    @GET
    public String fail(@PathParam("input") String input) {
        throw new IllegalStateException(
                "private-message-" + input, new RuntimeException("private-cause-" + input));
    }
}
