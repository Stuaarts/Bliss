package com.bliss.user;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bliss_user")
public class UserProfile extends PanacheEntityBase {
    @Id public UUID id;

    @Column(name = "oidc_subject", nullable = false, unique = true)
    public String subject;

    @Column(name = "display_name", nullable = false, length = 120)
    public String displayName;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
