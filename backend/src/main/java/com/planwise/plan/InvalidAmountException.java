package com.planwise.plan;

/** Thrown when a purchase amount is outside what PlanWise accepts. */
public class InvalidAmountException extends IllegalArgumentException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
