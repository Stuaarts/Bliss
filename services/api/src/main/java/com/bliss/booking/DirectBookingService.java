package com.bliss.booking;

import com.bliss.catalog.persistence.ServiceOffering;
import com.bliss.user.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class DirectBookingService {
    @Inject EntityManager em;
    @Inject ProfileService profiles;
    @Inject BookingCreation creation;

    public record Input(
            @NotNull @Min(0) Long offeringVersion, @NotBlank @Size(max = 120) String city) {}

    public record Result(BookingResource.View booking, boolean created) {}

    public Result book(UUID offeringId, UUID key, Input input) {
        var customer = profiles.requireOwnProfile();
        // Serialize a customer's keys across different offerings as well as identical retries.
        em.lock(customer, LockModeType.PESSIMISTIC_WRITE);
        String city = input.city().strip();
        Booking previous =
                Booking.find("customerId=?1 and directKey=?2", customer.id, key).firstResult();
        if (previous != null) {
            if (!offeringId.equals(previous.offeringId)
                    || !input.offeringVersion().equals(previous.offeringVersion)
                    || !city.equals(previous.city))
                throw new ClientErrorException("Booking key already used", 409);
            return new Result(BookingResource.view(previous), false);
        }
        ServiceOffering offering =
                em.find(ServiceOffering.class, offeringId, LockModeType.PESSIMISTIC_WRITE);
        if (offering == null || !offering.active) throw new NotFoundException();
        if (offering.version != input.offeringVersion())
            throw new ClientErrorException("Offering changed; review it again", 409);
        Number membership =
                (Number)
                        em.createNativeQuery(
                                        "SELECT count(*) FROM provider_member WHERE provider_id=?1 AND user_id=?2")
                                .setParameter(1, offering.provider.id)
                                .setParameter(2, customer.id)
                                .getSingleResult();
        if (membership.longValue() > 0)
            throw new ClientErrorException("Cannot book your own provider", 409);
        var booking = new Booking();
        booking.offeringId = offering.id;
        booking.offeringVersion = offering.version;
        booking.directKey = key;
        booking.customerId = customer.id;
        booking.providerId = offering.provider.id;
        booking.title = offering.title;
        booking.description = offering.description;
        booking.city = city;
        booking.price = offering.price;
        booking.currency = offering.currency;
        creation.persist(booking);
        return new Result(BookingResource.view(booking), true);
    }
}
