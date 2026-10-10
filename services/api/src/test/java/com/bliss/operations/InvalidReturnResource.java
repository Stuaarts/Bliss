package com.bliss.operations;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Test-only endpoint: never packaged into the application. */
@Path("/test/operations/invalid-return")
public class InvalidReturnResource {
    @GET
    @NotNull
    public String brokenContract() {
        return null;
    }
}
