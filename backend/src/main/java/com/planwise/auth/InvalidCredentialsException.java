package com.planwise.auth;

/** Same message for "no such user" and "wrong password", so attackers can't learn which emails exist. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
