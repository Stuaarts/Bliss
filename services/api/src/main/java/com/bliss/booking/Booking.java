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
        CREATED,
        WAIT_PROVIDER,
        SCHEDULED,
        IN_PROGRESS,
        WAIT_CUSTOMER,
        COMPLETED,
        CANCELLED,
        EXPIRED,
        DISPUTED,
        REFUNDED;

        public boolean terminal() {
            return this == COMPLETED || this == CANCELLED || this == EXPIRED || this == REFUNDED;
        }
    }

    @Id public UUID id;

    @Column(name = "request_id", unique = true)
    public UUID requestId;

    @Column(name = "quote_id", unique = true)
    public UUID quoteId;

    @Column(name = "offering_id")
    public UUID offeringId;

    @Column(name = "offering_version")
    public Long offeringVersion;

    @Column(name = "direct_key")
    public UUID directKey;

    @Column(nullable = false, length = 2000)
    public String description;

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

    @Column(name = "workflow_failed_at")
    public Instant workflowFailedAt;

    @Column(name = "scheduled_at")
    public Instant scheduledAt;

    @Column(name = "decision_deadline")
    public Instant decisionDeadline;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Version public long version;
}
