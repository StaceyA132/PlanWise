package com.planwise.auth;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email is too long")
        String email,

        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password,

        // Optional at sign-up; the AI assistant uses it later to judge affordability.
        @PositiveOrZero(message = "Monthly income cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Monthly income must be in dollars and cents")
        BigDecimal monthlyIncome) {
}
