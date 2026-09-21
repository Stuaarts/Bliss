package com.bliss.catalog.application;

import com.bliss.catalog.api.CatalogModels;
import com.bliss.catalog.persistence.ServiceCategory;
import com.bliss.catalog.persistence.ServiceOffering;
import com.bliss.provider.persistence.ProviderProfile;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class CatalogService {
    public List<CatalogModels.Category> categories() {
        return ServiceCategory.<ServiceCategory>listAll(Sort.by("name").and("id")).stream()
                .map(CatalogService::category)
                .toList();
    }

    public CatalogModels.Page<CatalogModels.Offering> offerings(
            UUID categoryId, int page, int size) {
        String predicate = "o.active = true";
        var parameters = new HashMap<String, Object>();
        if (categoryId != null) {
            predicate += " and o.category.id = :categoryId";
            parameters.put("categoryId", categoryId);
        }
        // Fetch to-one relationships with the bounded page; avoid N+1 reads.
        var query =
                ServiceOffering.<ServiceOffering>find(
                        "select o from ServiceOffering o join fetch o.provider join fetch o.category where "
                                + predicate
                                + " order by o.title, o.id",
                        parameters);
        long total = ServiceOffering.count(predicate.replace("o.", ""), parameters);
        var items = query.page(page, size).list().stream().map(CatalogService::offering).toList();
        return new CatalogModels.Page<>(items, page, size, total);
    }

    public CatalogModels.Offering offering(UUID id) {
        ServiceOffering entity =
                ServiceOffering.<ServiceOffering>find(
                                "select o from ServiceOffering o join fetch o.provider join fetch o.category "
                                        + "where o.id = ?1 and o.active = true",
                                id)
                        .firstResultOptional()
                        .orElseThrow(NotFoundException::new);
        return offering(entity);
    }

    public CatalogModels.Provider provider(UUID id) {
        ProviderProfile entity = ProviderProfile.findById(id);
        if (entity == null) throw new NotFoundException();
        return provider(entity);
    }

    private static CatalogModels.Category category(ServiceCategory entity) {
        return new CatalogModels.Category(entity.id, entity.slug, entity.name);
    }

    private static CatalogModels.Provider provider(ProviderProfile entity) {
        return new CatalogModels.Provider(
                entity.id,
                entity.displayName,
                entity.providerType.name(),
                entity.description,
                entity.city);
    }

    private static CatalogModels.Offering offering(ServiceOffering entity) {
        return new CatalogModels.Offering(
                entity.id,
                entity.title,
                entity.description,
                entity.price.toPlainString(),
                entity.currency,
                category(entity.category),
                provider(entity.provider),
                entity.version);
    }
}
