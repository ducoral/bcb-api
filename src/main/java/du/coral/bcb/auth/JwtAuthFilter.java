package du.coral.bcb.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = "authenticatedClientId";

    private static final List<String> PROTECTED_PREFIXES = List.of("/messages", "/conversations");
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final TokenStore tokenStore;

    public JwtAuthFilter(JwtService jwtService, TokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; // preflight de CORS não leva Authorization
        }
        var path = normalize(request.getRequestURI().substring(request.getContextPath().length()));
        return PROTECTED_PREFIXES.stream()
                .noneMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        var header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            reject(response, "Token ausente");
            return;
        }

        try {
            var claims = jwtService.validate(header.substring(BEARER.length()).trim());
            if (!tokenStore.isActive(claims.jti())) {
                reject(response, "Token revogado ou desconhecido");
                return;
            }
            request.setAttribute(CLIENT_ID_ATTRIBUTE, claims.clientId());
        } catch (JwtException | IllegalArgumentException e) {
            reject(response, "Token inválido ou expirado");
            return;
        }

        chain.doFilter(request, response);
    }

    /** Remove parâmetros de path (;x=y) e barras duplicadas */
    private static String normalize(String path) {
        return path.replaceAll(";[^/]*", "")
                .replaceAll("/{2,}", "/")
                .toLowerCase(Locale.ROOT);
    }

    private void reject(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"%s\"}"
                        .formatted(detail));
    }
}