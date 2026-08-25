package com.carddemo.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies the signed session token that replaces the legacy 160-byte COMMAREA.
 *
 * <p>The COBOL design carried user id and role in mutable screen state. FR-AUTH-005 requires that
 * authorization never trust a screen supplied value, so identity and role live in this signed token
 * and are re-checked server side on every request.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";

    private final SecretKey key;
    private final long expirationMinutes;
    private final String issuer;

    public JwtService(CardDemoProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "carddemo.jwt.secret must be configured with at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = properties.getJwt().getExpirationMinutes();
        this.issuer = properties.getJwt().getIssuer();
    }

    /** Builds an access token for a successfully authenticated user. */
    public String issue(String userId, String role, String displayName) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationMinutes * 60);
        return Jwts.builder()
                .subject(userId)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(CLAIM_ROLE, role, CLAIM_NAME, displayName))
                .signWith(key)
                .compact();
    }

    /** Returns the verified claims, or {@code null} when the token is absent, expired or invalid. */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return null;
        }
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
