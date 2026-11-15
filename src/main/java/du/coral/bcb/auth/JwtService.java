package du.coral.bcb.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    public record IssuedToken(String value, String jti, String clientId, Instant expiresAt, Duration ttl) {
    }

    public record TokenClaims(String jti, String clientId, Instant expiresAt) {
    }

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl:1h}") Duration ttl) {
        var bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret deve ter pelo menos 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = ttl;
    }

    public IssuedToken issue(String clientId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        String value = Jwts.builder()
                .id(jti)
                .subject(clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(value, jti, clientId, expiresAt, ttl);
    }

    public TokenClaims validate(String token) {
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getId() == null || claims.getSubject() == null || claims.getExpiration() == null) {
            throw new MalformedJwtException("Claims obrigatórias ausentes");
        }
        return new TokenClaims(claims.getId(), claims.getSubject(), claims.getExpiration().toInstant());
    }
}