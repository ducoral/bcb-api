package du.coral.bcb.client;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ClientFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH = "/clients";

    @Value("${app.admin.token:admin-token}")
    private String adminToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var isAdmin = request
                .getRequestURI()
                .startsWith(ADMIN_PATH);
        return !isAdmin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        var token = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (token != null)
            token = token.replace("Bearer ", "");
        if (!adminToken.equals(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"erro\":\"Não autorizado\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}