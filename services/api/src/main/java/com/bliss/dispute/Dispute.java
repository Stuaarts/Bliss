package com.bliss.dispute;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispute")
public class Dispute {
    public enum Status {
        COLLECTING_EVIDENCE,
        UNDER_REVIEW,
        RESOLVED_CUSTOMER,
        RESOLVED_PROVIDER;

        public boolean resolved() {
            return this == RESOLVED_CUSTOMER || this == RESOLVED_PROVIDER;
        }
    }

    @Id public UUID id;

    @Column(nullable = false, unique = true)
    public UUID bookingId;

    @Column(nullable = false)
    public UUID openedBy;

    @Column(nullable = false, length = 2000)
    public String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public Status status;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant evidenceDeadline;

    public Instant resolvedAt;

    @Column(unique = true)
    public String workflowInstanceId;

    public Instant workflowFailedAt;

    @Version public long version;
}
