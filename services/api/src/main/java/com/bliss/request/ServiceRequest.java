package com.bliss.request;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_request")
public class ServiceRequest extends PanacheEntityBase {
    public enum Status {
        OPEN,
        ACCEPTED,
        CANCELLED
    }

    @Id public UUID id;

    @Column(name = "customer_id", nullable = false)
    public UUID customerId;

    @Column(name = "category_id", nullable = false)
    public UUID categoryId;

    @Column(nullable = false, length = 160)
    public String title;

    @Column(nullable = false, length = 2000)
    public String description;

    @Column(nullable = false, length = 120)
    public String city;

    @Column(precision = 12, scale = 2)
    public BigDecimal budget;

    @Column(nullable = false, length = 3)
    public String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Version public long version;
}
