package com.bliss.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class RequestModels {
    private RequestModels() {}

    public record Input(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 120) String city,
            @NotNull UUID categoryId,
            String budget,
            @NotNull String currency) {}

    public record Update(@NotNull @Valid Input request, @NotNull @Min(0) Long version) {}

    public record View(
            UUID id,
            UUID categoryId,
            String title,
            String description,
            String city,
            String budget,
            String currency,
            String status,
            Instant createdAt,
            long version) {}

    public static View view(ServiceRequest r) {
        return new View(
                r.id,
                r.categoryId,
                r.title,
                r.description,
                r.city,
                r.budget == null ? null : r.budget.toPlainString(),
                r.currency,
                r.status.name(),
                r.createdAt,
                r.version);
    }
}
