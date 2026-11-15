package du.coral.bcb.auth;

public interface TokenStore {

    void save(JwtService.IssuedToken token);

    boolean isActive(String jti);

    void revoke(String jti);
}