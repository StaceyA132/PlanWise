package com.planwise.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.planwise.config.SecurityConfig;
import com.planwise.user.User;

/** Creates signed JWTs. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration expiration;

    public TokenService(JwtEncoder encoder, Clock clock,
                        @Value("${planwise.jwt.expiration}") Duration expiration) {
        this.encoder = encoder;
        this.clock = clock;
        this.expiration = expiration;
    }

    public AuthResponse issueFor(User user) {
        Instant now = clock.instant();
        // The token carries only the user id ("sub"), not the email or anything private.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.TOKEN_ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiration.toSeconds());
    }
}
