package du.coral.bcb.auth;

import du.coral.bcb.client.Client;
import du.coral.bcb.client.ClientRepository;
import du.coral.bcb.shared.error.InactiveClientException;
import du.coral.bcb.shared.error.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final ClientRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final String dummyHash;

    public AuthService(
            ClientRepository repository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenStore tokenStore) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional(readOnly = true)
    public JwtService.IssuedToken login(String id, String rawPassword) {
        var client = repository.findById(id);
        var hash = client
                .map(Client::getPassword)
                .orElse(dummyHash);
        var matches = passwordEncoder.matches(rawPassword, hash);

        if (client.isEmpty() || !matches)
            throw new InvalidCredentialsException();
        if (!client.get().isActive())
            throw new InactiveClientException();

        var token = jwtService.issue(id);
        tokenStore.save(token);
        return token;
    }
}