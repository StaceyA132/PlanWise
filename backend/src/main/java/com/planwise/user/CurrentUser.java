package com.planwise.user;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the logged-in user's id from a verified JWT (its "sub" claim). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
