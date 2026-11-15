package du.coral.bcb.auth;

import com.jayway.jsonpath.JsonPath;
import du.coral.bcb.client.Client;
import du.coral.bcb.client.ClientRepository;
import du.coral.bcb.client.DocumentType;
import du.coral.bcb.client.PlanType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.jwt.secret=" + AuthIT.SECRET, "app.jwt.ttl=1h"})
@Testcontainers
class AuthIT {

    static final String SECRET = "test-secret-test-secret-test-secret-0123";
    static final String OTHER_SECRET = "another-secret-another-secret-another-0";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired WebApplicationContext context;
    @Autowired ClientRepository repository;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwtService;
    @Autowired TokenStore tokenStore;
    @Autowired JwtAuthFilter jwtAuthFilter;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(jwtAuthFilter)
                .build();
        repository.deleteAll();
        repository.save(client(true));
    }

    // ---------- POST /auth/login ----------
    @Test
    void shouldIssueJwtWhenCredentialsAreValid() throws Exception {
        var result = mockMvc.perform(loginRequest("client123", "senha123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        var claims = jwtService.validate(token);
        assertThat(claims.clientId()).isEqualTo("client123");
        assertThat(tokenStore.isActive(claims.jti())).isTrue();
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(loginRequest("client123", "errada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUnknownClient() throws Exception {
        mockMvc.perform(loginRequest("inexistente", "senha123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenForInactiveClient() throws Exception {
        repository.save(client(false));

        mockMvc.perform(loginRequest("client123", "senha123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnBadRequestWhenFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ---------- Filtro JWT ----------
    @ParameterizedTest
    @ValueSource(strings = {
            "/messages", "/messages/123", "/conversations", "/conversations/abc/messages",
            "/messages;jsessionid=x"})
    void shouldRejectProtectedPathWithoutToken(String path) throws Exception {
        assertUnauthorized(get(path));
    }

    @Test
    void shouldRejectMalformedToken() throws Exception {
        assertUnauthorized(get("/messages").header("Authorization", "Bearer abc.def.ghi"));
    }

    @Test
    void shouldRejectTokenSignedWithAnotherKey() throws Exception {
        var token = jwt(UUID.randomUUID().toString(), Instant.now().plusSeconds(600), OTHER_SECRET);
        assertUnauthorized(get("/messages").header("Authorization", "Bearer " + token));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        var token = jwt(UUID.randomUUID().toString(), Instant.now().minusSeconds(60), SECRET);
        assertUnauthorized(get("/messages").header("Authorization", "Bearer " + token));
    }

    @Test
    void shouldRejectValidSignatureWhenTokenIsNotInStore() throws Exception {
        var token = jwt(UUID.randomUUID().toString(), Instant.now().plusSeconds(600), SECRET);
        assertUnauthorized(get("/conversations").header("Authorization", "Bearer " + token));
    }

    @Test
    void shouldRejectRevokedToken() throws Exception {
        var token = login();
        tokenStore.revoke(jwtService.validate(token).jti());
        assertUnauthorized(get("/messages").header("Authorization", "Bearer " + token));
    }

    @Test
    void shouldLetValidTokenReachProtectedEndpoints() throws Exception {
        var token = login();

        // O filtro não bloqueia; o status final depende do controller (200, 404 etc.), nunca 401.
        mockMvc.perform(get("/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().is(not(401)));
        mockMvc.perform(get("/conversations").header("Authorization", "Bearer " + token))
                .andExpect(status().is(not(401)));
    }

    // ---------- helpers ----------
    private Client client(boolean active) {
        return new Client(
                "client123",
                "Empresa ABC",
                encoder.encode("senha123"),
                "12345678000199",
                DocumentType.CNPJ,
                PlanType.PREPAID,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                active);
    }

    private MockHttpServletRequestBuilder loginRequest(String id, String password) {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id": "%s", "password": "%s"}
                        """.formatted(id, password));
    }

    private String login() throws Exception {
        var body = mockMvc.perform(loginRequest("client123", "senha123"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String jwt(String jti, Instant expiresAt, String secret) {
        return Jwts.builder()
                .id(jti)
                .subject("client123")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
    }
}