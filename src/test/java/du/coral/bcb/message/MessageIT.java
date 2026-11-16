package du.coral.bcb.message;

import com.jayway.jsonpath.JsonPath;
import du.coral.bcb.auth.JwtAuthFilter;
import du.coral.bcb.auth.JwtService;
import du.coral.bcb.auth.TokenStore;
import du.coral.bcb.client.Client;
import du.coral.bcb.client.ClientRepository;
import du.coral.bcb.client.DocumentType;
import du.coral.bcb.client.PlanType;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123")
@Testcontainers
class MessageIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired WebApplicationContext context;
    @Autowired MessageRepository messageRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired MessageQueue queue;
    @Autowired MessageQueueInitializer initializer;
    @Autowired JwtService jwtService;
    @Autowired TokenStore tokenStore;
    @Autowired JwtAuthFilter jwtAuthFilter;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(jwtAuthFilter)
                .build();
        messageRepository.deleteAll();   // antes dos clientes, por causa da FK
        clientRepository.deleteAll();
        clientRepository.save(new Client(
                "client123", "Empresa ABC", "hash-irrelevante",
                "12345678000199", DocumentType.CNPJ, PlanType.PREPAID,
                BigDecimal.TEN, BigDecimal.ZERO, true));
        queue.clear();
    }

    @Test
    void shouldPersistAndEnqueueMessageWithServerDefinedFields() throws Exception {
        var body = """
                {
                  "conversationId": "conv456",
                  "recipientId": "user789",
                  "content": "Olá, como vai?"
                }
                """;

        var result = mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.conversationId").value("conv456"))
                .andExpect(jsonPath("$.senderId").value("client123"))   // vem do token
                .andExpect(jsonPath("$.recipientId").value("user789"))
                .andExpect(jsonPath("$.content").value("Olá, como vai?"))
                .andExpect(jsonPath("$.priority").value("normal"))      // padrão
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.cost").value(0.25))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(id).startsWith("msg-");

        var saved = messageRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(saved.getSenderId()).isEqualTo("client123");
        assertThat(saved.getCost()).isEqualByComparingTo("0.25");

        assertThat(queue.snapshot()).extracting(Message::getId).containsExactly(id);
    }

    @Test
    void shouldChargeUrgentCost() throws Exception {
        var body = """
                {
                  "conversationId": "conv456",
                  "recipientId": "user789",
                  "content": "Preciso de retorno agora",
                  "priority": "urgent"
                }
                """;

        var result = mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.priority").value("urgent"))
                .andExpect(jsonPath("$.cost").value(0.5))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(messageRepository.findById(id).orElseThrow().getCost())
                .isEqualByComparingTo("0.50");
    }

    @Test
    void shouldKeepArrivalOrderInQueue() throws Exception {
        var first = sendAndGetId("primeira");
        var second = sendAndGetId("segunda");

        assertThat(queue.poll()).map(Message::getId).contains(first);
        assertThat(queue.poll()).map(Message::getId).contains(second);
        assertThat(queue.poll()).isEmpty();
    }

    @Test
    void shouldReturnUnauthorizedWithoutTokenAndStoreNothing() throws Exception {
        mockMvc.perform(post("/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("sem token")))
                .andExpect(status().isUnauthorized());

        assertThat(messageRepository.count()).isZero();
        assertThat(queue.size()).isZero();
    }

    @Test
    void shouldReturnBadRequestWhenFieldsAreMissingAndStoreNothing() throws Exception {
        mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.conversationId").exists())
                .andExpect(jsonPath("$.errors.recipientId").exists())
                .andExpect(jsonPath("$.errors.content").exists());

        assertThat(messageRepository.count()).isZero();
        assertThat(queue.size()).isZero();
    }

    @Test
    void shouldReturnBadRequestWhenPriorityIsUnknown() throws Exception {
        String body = validBody("oi").replace("\"content\"", "\"priority\": \"high\", \"content\"");

        mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(messageRepository.count()).isZero();
    }

    // ---------- Carga inicial ----------

    @Test
    void shouldLoadOnlyQueuedMessagesOldestFirstOnStartup() {
        Instant base = Instant.parse("2026-11-15T10:00:00Z");
        messageRepository.saveAll(List.of(
                stored("msg-new-queued", base.plusSeconds(60), MessageStatus.QUEUED),
                stored("msg-sent", base.plusSeconds(10), MessageStatus.SENT),
                stored("msg-old-queued", base, MessageStatus.QUEUED),
                stored("msg-failed", base.plusSeconds(20), MessageStatus.FAILED),
                stored("msg-processing", base.plusSeconds(30), MessageStatus.PROCESSING)));
        queue.clear();

        int loaded = initializer.load();

        assertThat(loaded).isEqualTo(2);
        assertThat(queue.snapshot()).extracting(Message::getId)
                .containsExactly("msg-old-queued", "msg-new-queued");
    }

    @Test
    void shouldNotDuplicateMessagesWhenLoadRunsTwice() {
        messageRepository.save(stored("msg-1", Instant.parse("2026-11-15T10:00:00Z"), MessageStatus.QUEUED));
        queue.clear();

        assertThat(initializer.load()).isEqualTo(1);
        assertThat(initializer.load()).isZero();
        assertThat(queue.size()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private String bearer() {
        var token = jwtService.issue("client123");
        tokenStore.save(token);
        return "Bearer " + token.value();
    }

    private String validBody(String content) {
        return """
                {
                  "conversationId": "conv456",
                  "recipientId": "user789",
                  "content": "%s"
                }
                """.formatted(content);
    }

    private String sendAndGetId(String content) throws Exception {
        var response = mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(content)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private Message stored(String id, Instant timestamp, MessageStatus status) {
        return new Message(
                id,
                "conv456",
                "client123",
                "user789",
                "conteúdo",
                timestamp,
                MessagePriority.NORMAL,
                status,
                new BigDecimal("0.25"));
    }
}