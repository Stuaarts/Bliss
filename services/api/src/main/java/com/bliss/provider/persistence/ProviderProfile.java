package com.bliss.provider.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_profile")
public class ProviderProfile extends PanacheEntityBase {
    public enum Type {
        INDIVIDUAL,
        COMPANY
    }

    @Id public UUID id;

    @Column(name = "display_name", nullable = false, length = 120)
    public String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 20)
    public Type providerType;

    @Column(nullable = false, length = 2000)
    public String description;

    @Column(nullable = false, length = 120)
    public String city;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Version public long version;
}
