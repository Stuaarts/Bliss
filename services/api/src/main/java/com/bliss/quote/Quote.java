package com.bliss.quote;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quote")
public class Quote extends PanacheEntityBase {
    public enum Status {
        SUBMITTED,
        ACCEPTED,
        DECLINED
    }

    @Id public UUID id;

    @Column(name = "request_id", nullable = false)
    public UUID requestId;

    @Column(name = "provider_id", nullable = false)
    public UUID providerId;

    @Column(nullable = false, length = 2000)
    public String description;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal price;

    @Column(nullable = false, length = 3)
    public String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Version public long version;
}
