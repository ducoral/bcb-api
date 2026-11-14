package du.coral.bcp.admin;

import du.coral.bcp.client.Client;
import du.coral.bcp.client.ClientRepository;
import du.coral.bcp.client.DocumentType;
import du.coral.bcp.client.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
class AdminControllerIT {

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

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    MockMvc mockMvc;

    @Autowired
    WebApplicationContext context;

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
        mockMvc.perform(post("/admin/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/admin/client/client123"))
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

        mockMvc.perform(post("/admin/client")
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

        mockMvc.perform(post("/admin/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnConflictWhenDocumentAlreadyExists() throws Exception {
        repository.save(existingClient("other-id", "12345678000199"));

        mockMvc.perform(post("/admin/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnBadRequestWhenDocumentDoesNotMatchType() throws Exception {
        String body = VALID_BODY.replace("\"CNPJ\"", "\"CPF\"");

        mockMvc.perform(post("/admin/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.documentConsistent").exists());

        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldReturnBadRequestWhenDocumentHasInvalidFormat() throws Exception {
        String body = VALID_BODY.replace("12345678000199", "123.456.780/0019");

        mockMvc.perform(post("/admin/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.documentId").exists());
    }

    @Test
    void shouldReturnBadRequestWhenBalanceIsNegative() throws Exception {
        String body = VALID_BODY.replace("\"balance\": 100.00", "\"balance\": -1");

        mockMvc.perform(post("/admin/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.balance").exists());
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/admin/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.documentType").exists())
                .andExpect(jsonPath("$.errors.planType").exists());
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
}