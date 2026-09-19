package com.bliss.catalog.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "service_category")
public class ServiceCategory extends PanacheEntityBase {
    @Id public UUID id;

    @Column(nullable = false, unique = true, length = 80)
    public String slug;

    @Column(nullable = false, length = 120)
    public String name;
}
