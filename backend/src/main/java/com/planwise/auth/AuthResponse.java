package com.planwise.auth;

/** Returned by register and login. The client sends {@code token} as "Authorization: Bearer <token>". */
public record AuthResponse(String token, String tokenType, long expiresInSeconds) {
}
