package com.bliss.booking;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class Booking extends PanacheEntityBase {
    public enum Status {
        CREATED
    }

    @Id public UUID id;

    @Column(name = "request_id", nullable = false, unique = true)
    public UUID requestId;

    @Column(name = "quote_id", nullable = false, unique = true)
    public UUID quoteId;

    @Column(name = "customer_id", nullable = false)
    public UUID customerId;

    @Column(name = "provider_id", nullable = false)
    public UUID providerId;

    @Column(nullable = false, length = 160)
    public String title;

    @Column(nullable = false, length = 120)
    public String city;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal price;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    public Status status;

    @Column(name = "workflow_instance_id")
    public String workflowInstanceId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Version public long version;
}
