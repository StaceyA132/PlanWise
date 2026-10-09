package com.planwise.plan;

public class PaymentAlreadyPaidException extends RuntimeException {

    public PaymentAlreadyPaidException() {
        super("This payment has already been paid");
    }
}
