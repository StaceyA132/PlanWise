package com.planwise.auth;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planwise.user.User;
import com.planwise.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // A real BCrypt hash to compare against when the email doesn't exist (see login).
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user;
        try {
            // Only the BCrypt hash is stored, never the password itself.
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()),
                    request.monthlyIncome()));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the UNIQUE constraint catches the second.
            throw new EmailAlreadyRegisteredException();
        }
        return tokenService.issueFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        if (user == null) {
            // Still run a BCrypt check so "unknown email" takes as long as "wrong password".
            // Otherwise response time would reveal which emails have accounts.
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issueFor(user);
    }

    /** "  Sam@Example.com " and "sam@example.com" are the same account. */
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
