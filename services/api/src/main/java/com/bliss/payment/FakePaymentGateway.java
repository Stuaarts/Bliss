package com.bliss.payment;

import com.bliss.booking.Booking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Local simulation only. Caller holds the booking lock; ledger changes share its transaction. */
@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class FakePaymentGateway implements PaymentGateway {
    @Inject EntityManager em;

    public void execute(Booking b, Operation operation) {
        Number existing =
                (Number)
                        em.createNativeQuery(
                                        "SELECT count(*) FROM payment_transaction WHERE booking_id=?1 AND operation=?2")
                                .setParameter(1, b.id)
                                .setParameter(2, operation.name())
                                .getSingleResult();
        if (existing.longValue() > 0) return;
        var rows =
                em.createNativeQuery("SELECT status FROM payment WHERE booking_id=?1")
                        .setParameter(1, b.id)
                        .getResultList();
        String status = rows.isEmpty() ? null : (String) rows.getFirst();
        if (operation == Operation.VOID && status == null) return;
        String next =
                switch (operation) {
                    case AUTHORIZE -> {
                        if (status != null)
                            throw new IllegalStateException("Payment already exists");
                        yield "AUTHORIZED";
                    }
                    case CAPTURE -> {
                        if (!"AUTHORIZED".equals(status))
                            throw new IllegalStateException("Payment is not authorized");
                        yield "CAPTURED";
                    }
                    case VOID -> {
                        if (!"AUTHORIZED".equals(status))
                            throw new IllegalStateException("Payment cannot be voided");
                        yield "VOIDED";
                    }
                    case REFUND -> {
                        if (!"CAPTURED".equals(status))
                            throw new IllegalStateException("Payment is not captured");
                        yield "REFUNDED";
                    }
                };
        if (status == null)
            em.createNativeQuery(
                            "INSERT INTO payment(booking_id,amount,currency,status) VALUES (?1,?2,?3,?4)")
                    .setParameter(1, b.id)
                    .setParameter(2, b.price)
                    .setParameter(3, b.currency)
                    .setParameter(4, next)
                    .executeUpdate();
        else
            em.createNativeQuery("UPDATE payment SET status=?2 WHERE booking_id=?1")
                    .setParameter(1, b.id)
                    .setParameter(2, next)
                    .executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO payment_transaction(id,booking_id,operation) VALUES (?1,?2,?3)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, b.id)
                .setParameter(3, operation.name())
                .executeUpdate();
    }
}
