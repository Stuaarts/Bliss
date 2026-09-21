package com.bliss.catalog.api;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public final class CatalogModels {
    private CatalogModels() {}

    public record Category(UUID id, String slug, String name) {}

    public record Provider(
            UUID id, String displayName, String type, String description, String city) {}

    public record Offering(
            UUID id,
            String title,
            String description,
            @Schema(
                            description = "Exact decimal amount, encoded as a string",
                            examples = {"80.00"})
                    String price,
            String currency,
            Category category,
            Provider provider,
            long version) {}

    public record Page<T>(List<T> items, int page, int size, long total) {}
}
