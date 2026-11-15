package du.coral.bcb.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryTokenStore implements TokenStore {

    private record StoredToken(String clientId, Instant expiresAt) {
    }

    private final ConcurrentMap<String, StoredToken> tokens = new ConcurrentHashMap<>();

    @Override
    public void save(JwtService.IssuedToken token) {
        purgeExpired();
        tokens.put(token.jti(), new StoredToken(token.clientId(), token.expiresAt()));
    }

    @Override
    public boolean isActive(String jti) {
        var stored = tokens.get(jti);
        if (stored == null)
            return false;
        if (stored.expiresAt().isBefore(Instant.now())) {
            tokens.remove(jti);
            return false;
        }
        return true;
    }

    @Override
    public void revoke(String jti) {
        tokens.remove(jti);
    }

    private void purgeExpired() {
        tokens
                .values()
                .removeIf(token -> token.expiresAt().isBefore(Instant.now()));
    }
}