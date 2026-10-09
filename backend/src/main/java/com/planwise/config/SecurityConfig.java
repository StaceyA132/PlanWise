package com.planwise.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Who can call what, and how JWTs are signed and checked.
 *
 * <p>Clients log in once, get a signed token, and send it on every request as
 * {@code Authorization: Bearer <token>}. The server keeps no session: the signature alone
 * proves the token is genuine.
 */
@Configuration
public class SecurityConfig {

    public static final String TOKEN_ISSUER = "planwise";

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final int MIN_SECRET_BYTES = 32; // HS256 needs a key of at least 256 bits

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is for cookie-based logins; a bearer token can't be sent by another site.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/quotes").permitAll() // saves nothing
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // Read and verify "Authorization: Bearer <jwt>" on every request; 401 if missing or invalid.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** BCrypt hashes passwords with a random salt and is deliberately slow, which makes guessing expensive. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${planwise.jwt.secret}") String secret) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set; using a random secret. Tokens will stop working after a restart.");
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " characters");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** Signs new tokens (used at login). */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /** Verifies incoming tokens: signature, expiry, and that we issued them. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TOKEN_ISSUER));
        return decoder;
    }
}
