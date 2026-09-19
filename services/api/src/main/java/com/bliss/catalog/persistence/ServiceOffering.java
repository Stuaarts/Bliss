package com.bliss.catalog.persistence;

import com.bliss.provider.persistence.ProviderProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_offering")
public class ServiceOffering extends PanacheEntityBase {
    @Id public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    public ProviderProfile provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    public ServiceCategory category;

    @Column(nullable = false, length = 160)
    public String title;

    @Column(nullable = false, length = 2000)
    public String description;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal price;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(nullable = false)
    public boolean active;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Version public long version;
}
