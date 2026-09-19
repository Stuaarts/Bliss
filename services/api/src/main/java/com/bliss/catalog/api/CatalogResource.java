package com.bliss.catalog.api;

import com.bliss.catalog.application.CatalogService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog", description = "Public service discovery")
public class CatalogResource {
    @Inject CatalogService catalog;

    @GET
    @Path("/categories")
    @Operation(summary = "Browse reference service categories")
    public List<CatalogModels.Category> categories() {
        return catalog.categories();
    }

    @GET
    @Path("/offerings")
    @Operation(summary = "Browse active offerings with bounded pagination")
    public CatalogModels.Page<CatalogModels.Offering> offerings(
            @QueryParam("categoryId") UUID categoryId,
            @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return catalog.offerings(categoryId, page, size);
    }

    @GET
    @Path("/offerings/{id}")
    @Operation(summary = "Read an active offering")
    public CatalogModels.Offering offering(@PathParam("id") UUID id) {
        return catalog.offering(id);
    }

    @GET
    @Path("/providers/{id}")
    @Operation(summary = "Read a public provider profile")
    public CatalogModels.Provider provider(@PathParam("id") UUID id) {
        return catalog.provider(id);
    }
}
