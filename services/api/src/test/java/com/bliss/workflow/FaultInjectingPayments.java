package com.bliss.workflow;

import com.bliss.booking.Booking;
import com.bliss.payment.*;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Alternative
@Priority(1)
@ApplicationScoped
public class FaultInjectingPayments implements PaymentGateway {
    @Inject FakePaymentGateway delegate;
    final ConcurrentHashMap<UUID, AtomicInteger> remaining = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

    public void failNext(UUID booking, int times) {
        remaining.put(booking, new AtomicInteger(times));
        attempts.put(booking, new AtomicInteger());
    }

    public void execute(Booking booking, Operation operation) {
        // Fail after ledger writes to prove the surrounding transaction rolls them back.
        delegate.execute(booking, operation);
        var failures = remaining.get(booking.id);
        if (failures != null) {
            attempts.get(booking.id).incrementAndGet();
            if (failures.getAndDecrement() > 0) throw new TransientFailure();
        }
    }

    public int attempts(UUID booking) {
        return attempts.get(booking).get();
    }
}
