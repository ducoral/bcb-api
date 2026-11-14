package du.coral.bcb.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
class ClientControllerIT {

    static final String VALID_BODY = """
            {
              "id": "client123",
              "name": "Empresa ABC Ltda",
              "password": "senha123",
              "documentId": "12345678000199",
              "documentType": "CNPJ",
              "planType": "prepaid",
              "balance": 100.00,
              "limit": 0,
              "active": true
            }
            """;

    static final String UPDATE_BODY = """
        {
          "name": "Empresa ABC S.A.",
          "documentId": "12345678000199",
          "documentType": "CNPJ",
          "planType": "postpaid",
          "balance": 0,
          "limit": 5000.00,
          "active": false
        }
        """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    MockMvc mockMvc;

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ClientRepository repository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();
        repository.deleteAll();
    }

    @Test
    void shouldCreateClientAndPersistIt() throws Exception {
        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/clients/client123"))
                .andExpect(jsonPath("$.id").value("client123"))
                .andExpect(jsonPath("$.documentType").value("CNPJ"))
                .andExpect(jsonPath("$.planType").value("PREPAID"))
                .andExpect(jsonPath("$.balance").value(100.0))
                .andExpect(jsonPath("$.active").value(true));

        var saved = repository.findById("client123").orElseThrow();
        assertThat(saved.getName()).isEqualTo("Empresa ABC Ltda");
        assertThat(saved.getDocumentId()).isEqualTo("12345678000199");
        assertThat(saved.getBalance()).isEqualByComparingTo("100.00");
        assertThat(saved.getLimit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldApplyDefaultsWhenOptionalFieldsAreOmitted() throws Exception {
        String body = """
                {
                  "id": "client456",
                  "name": "João da Silva",
                  "password": "senha123",
                  "documentId": "12345678901",
                  "documentType": "CPF",
                  "planType": "postpaid"
                }
                """;

        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.limit").value(0));
    }

    @Test
    void shouldReturnConflictWhenIdAlreadyExists() throws Exception {
        repository.save(existingClient("client123", "99999999000100"));

        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnConflictWhenDocumentAlreadyExists() throws Exception {
        repository.save(existingClient("other-id", "12345678000199"));

        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnBadRequestWhenDocumentDoesNotMatchType() throws Exception {
        String body = VALID_BODY.replace("\"CNPJ\"", "\"CPF\"");

        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.documentConsistent").exists());

        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldReturnBadRequestWhenDocumentHasInvalidFormat() throws Exception {
        String body = VALID_BODY.replace("12345678000199", "123.456.780/0019");

        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.documentId").exists());
    }

    @Test
    void shouldReturnBadRequestWhenBalanceIsNegative() throws Exception {
        String body = VALID_BODY.replace("\"balance\": 100.00", "\"balance\": -1");

        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.balance").exists());
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/clients/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.documentType").exists())
                .andExpect(jsonPath("$.errors.planType").exists());
    }

    @Test
    void shouldListClientsOrderedById() throws Exception {
        repository.save(existingClient("client-b", "22222222000122"));
        repository.save(existingClient("client-a", "11111111000111"));

        mockMvc.perform(get("/clients/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("client-a"))
                .andExpect(jsonPath("$[1].id").value("client-b"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoClients() throws Exception {
        mockMvc.perform(get("/clients/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldUpdateClientAndPersistChanges() throws Exception {
        repository.save(existingClient("client123", "12345678000199"));

        mockMvc.perform(put("/clients/client123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("client123"))
                .andExpect(jsonPath("$.name").value("Empresa ABC S.A."))
                .andExpect(jsonPath("$.planType").value("POSTPAID"))
                .andExpect(jsonPath("$.limit").value(5000.0))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());

        var saved = repository.findById("client123").orElseThrow();
        assertThat(saved.getName()).isEqualTo("Empresa ABC S.A.");
        assertThat(saved.getPlanType()).isEqualTo(PlanType.POSTPAID);
        assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getLimit()).isEqualByComparingTo("5000.00");
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void shouldKeepPasswordWhenItIsOmitted() throws Exception {
        repository.save(existingClient("client123", "12345678000199"));

        mockMvc.perform(put("/clients/client123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk());

        assertThat(passwordOf("client123")).isEqualTo("senha123");
    }

    @Test
    void shouldReplacePasswordWhenItIsProvided() throws Exception {
        repository.save(existingClient("client123", "12345678000199"));
        String body = UPDATE_BODY.replace("\"name\"", "\"password\": \"nova-senha\", \"name\"");

        mockMvc.perform(put("/clients/client123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(passwordOf("client123")).isNotEqualTo("senha123");
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingClient() throws Exception {
        mockMvc.perform(put("/clients/inexistente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenDocumentBelongsToAnotherClient() throws Exception {
        repository.save(existingClient("client123", "12345678000199"));
        repository.save(existingClient("client456", "99999999000100"));

        mockMvc.perform(put("/clients/client456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isConflict());

        assertThat(repository.findById("client456").orElseThrow().getDocumentId())
                .isEqualTo("99999999000100");
    }

    @Test
    void shouldReturnBadRequestWhenUpdateDocumentDoesNotMatchType() throws Exception {
        repository.save(existingClient("client123", "12345678000199"));
        String body = UPDATE_BODY.replace("\"CNPJ\"", "\"CPF\"");

        mockMvc.perform(put("/clients/client123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.documentConsistent").exists());
    }

    @Test
    void shouldReturnBalanceAndLimit() throws Exception {
        repository.save(new Client("client123", "Empresa ABC", "senha123", "12345678000199",
                DocumentType.CNPJ, PlanType.POSTPAID,
                new BigDecimal("0.00"), new BigDecimal("5000.00"), true));

        mockMvc.perform(get("/clients/client123/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("client123"))
                .andExpect(jsonPath("$.planType").value("POSTPAID"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.limit").value(5000.0));
    }

    @Test
    void shouldReturnNotFoundWhenQueryingBalanceOfMissingClient() throws Exception {
        mockMvc.perform(get("/clients/inexistente/balance"))
                .andExpect(status().isNotFound());
    }

    private Client existingClient(String id, String documentId) {
        return new Client(
                id,
                "Cliente Existente",
                "senha123",
                documentId,
                DocumentType.CNPJ,
                PlanType.PREPAID,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                true);
    }

    private String passwordOf(String id) {
        return jdbc.queryForObject("select password from client where id = ?", String.class, id);
    }
}