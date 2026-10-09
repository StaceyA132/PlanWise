package com.planwise.user;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UserController {

    public record MeResponse(Long id, String email, BigDecimal monthlyIncome) {
    }

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    /** The logged-in user's profile. Requires a valid token. */
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.findById(CurrentUser.id(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MeResponse(user.getId(), user.getEmail(), user.getMonthlyIncome());
    }
}
