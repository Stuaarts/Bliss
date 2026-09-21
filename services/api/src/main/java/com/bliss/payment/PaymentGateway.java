package com.bliss.payment;

import com.bliss.booking.Booking;

public interface PaymentGateway {
    enum Operation {
        AUTHORIZE,
        CAPTURE,
        VOID,
        REFUND
    }

    void execute(Booking booking, Operation operation);

    class TransientFailure extends RuntimeException {
        public TransientFailure() {
            super("Simulated temporary payment failure");
        }
    }
}
