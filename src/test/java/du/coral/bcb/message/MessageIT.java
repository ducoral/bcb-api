package du.coral.bcb.message;

import com.jayway.jsonpath.JsonPath;
import du.coral.bcb.auth.JwtAuthFilter;
import du.coral.bcb.auth.JwtService;
import du.coral.bcb.auth.TokenStore;
import du.coral.bcb.billing.MonthlyLimitResetJob;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123")
@Testcontainers
class MessageIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    WebApplicationContext context;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    ClientRepository clientRepository;

    @Autowired
    MessageQueue queue;

    @Autowired
    MessageQueueInitializer initializer;

    @Autowired
    JwtService jwtService;

    @Autowired
    TokenStore tokenStore;

    @Autowired
    JwtAuthFilter jwtAuthFilter;

    @Autowired
    Clock clock;

    @Autowired
    MonthlyLimitResetJob resetJob;

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
                  "recipientId": "user789",
                  "content": "Olá, como vai?"
                }
                """;

        var result = mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
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
                .andExpect(jsonPath("$.errors.recipientId").exists())
                .andExpect(jsonPath("$.errors.content").exists());

        assertThat(messageRepository.count()).isZero();
        assertThat(queue.size()).isZero();
    }

    @Test
    void shouldReturnBadRequestWhenPriorityIsUnknown() throws Exception {
        var body = validBody("oi").replace("\"content\"", "\"priority\": \"high\", \"content\"");

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
        var base = Instant.parse("2026-11-15T10:00:00Z");
        messageRepository.saveAll(List.of(
                stored("msg-new-queued", base.plusSeconds(60), MessageStatus.QUEUED),
                stored("msg-sent", base.plusSeconds(10), MessageStatus.SENT),
                stored("msg-old-queued", base, MessageStatus.QUEUED),
                stored("msg-failed", base.plusSeconds(20), MessageStatus.FAILED),
                stored("msg-processing", base.plusSeconds(30), MessageStatus.PROCESSING)));
        queue.clear();

        int loaded = initializer.load();
        assertThat(loaded).isEqualTo(2);
    }

    @Test
    void shouldNotDuplicateMessagesWhenLoadRunsTwice() {
        messageRepository.save(stored("msg-1", Instant.parse("2026-11-15T10:00:00Z"), MessageStatus.QUEUED));
        queue.clear();

        assertThat(initializer.load()).isEqualTo(1);
        assertThat(initializer.load()).isZero();
        assertThat(queue.size()).isEqualTo(1);
    }

    // ---------- Pré-pago
    @Test
    void shouldDebitBalanceForEachMessage() throws Exception {
        seed(PlanType.PREPAID, "10.00", "0.00");

        for (int i = 0; i < 5; i++) {
            send("mensagem " + i).andExpect(status().isAccepted());
        }

        assertThat(balanceOf("client123")).isEqualByComparingTo("8.75");   // 10 - 5 x 0.25
        assertThat(messageRepository.count()).isEqualTo(5);
        assertThat(queue.size()).isEqualTo(5);
    }

    @Test
    void shouldDebitUrgentCost() throws Exception {
        seed(PlanType.PREPAID, "10.00", "0.00");
        var body = validBody("urgente").replace("\"content\"", "\"priority\": \"urgent\", \"content\"");

        mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        assertThat(balanceOf("client123")).isEqualByComparingTo("9.50");
    }

    @Test
    void shouldRejectWhenBalanceIsInsufficient() throws Exception {
        seed(PlanType.PREPAID, "0.20", "0.00");

        send("sem saldo")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.required").value(0.25))
                .andExpect(jsonPath("$.available").value(0.2));

        assertThat(balanceOf("client123")).isEqualByComparingTo("0.20");
        assertThat(messageRepository.count()).isZero();
        assertThat(queue.size()).isZero();
    }

    @Test
    void shouldAllowSpendingExactBalanceAndThenReject() throws Exception {
        seed(PlanType.PREPAID, "0.25", "0.00");

        send("última").andExpect(status().isAccepted());
        assertThat(balanceOf("client123")).isEqualByComparingTo("0.00");

        send("sem saldo").andExpect(status().isPaymentRequired());
        assertThat(messageRepository.count()).isEqualTo(1);
    }

    // ---------- Pós-pago ---------
    @Test
    void shouldConsumeRemainingMonthlyLimit() throws Exception {
        // limite mensal 50, já usou 40 neste mês: restam 10
        var client = newClient("client123", "12345678000199", PlanType.POSTPAID, "0.00", "10.00", true);
        client.setMonthlyLimit(new BigDecimal("50.00"));
        client.renewLimitIfNewPeriod(currentPeriod());
        clientRepository.save(client);

        for (int i = 0; i < 10; i++) {
            send("mensagem " + i).andExpect(status().isAccepted());
        }

        assertThat(limitOf("client123")).isEqualByComparingTo("7.50");
        assertThat(balanceOf("client123")).isEqualByComparingTo("0.00");   // pós-pago não mexe no saldo
        assertThat(queue.size()).isEqualTo(10);
    }

    @Test
    void shouldRejectWhenMonthlyLimitIsExhausted() throws Exception {
        var client = newClient("client123", "12345678000199", PlanType.POSTPAID, "0.00", "0.20", true);
        client.setMonthlyLimit(new BigDecimal("50.00"));
        client.renewLimitIfNewPeriod(currentPeriod());
        clientRepository.save(client);

        send("sem limite")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.detail").value("Limite mensal insuficiente"));

        assertThat(limitOf("client123")).isEqualByComparingTo("0.20");
        assertThat(messageRepository.count()).isZero();
        assertThat(queue.size()).isZero();
    }

    @Test
    void shouldRenewLimitWhenMonthChanges() throws Exception {
        // restam 0.00 do mês passado: ao enviar neste mês, o limite volta a 50 e debita 0.25
        var client = newClient("client123", "12345678000199", PlanType.POSTPAID, "0.00", "0.00", true);
        client.setMonthlyLimit(new BigDecimal("50.00"));
        client.renewLimitIfNewPeriod(currentPeriod().minusMonths(1));
        clientRepository.save(client);

        send("novo mês").andExpect(status().isAccepted());

        var saved = clientRepository.findById("client123").orElseThrow();
        assertThat(saved.getLimit()).isEqualByComparingTo("49.75");
        assertThat(saved.getLimitPeriod()).isEqualTo(currentPeriod());
    }

    @Test
    void shouldResetOnlyPostpaidLimitsInScheduledJob() {
        var postpaid = newClient("client123", "12345678000199", PlanType.POSTPAID, "0.00", "1.00", true);
        postpaid.setMonthlyLimit(new BigDecimal("50.00"));
        postpaid.renewLimitIfNewPeriod(currentPeriod().minusMonths(1));
        clientRepository.save(postpaid);
        clientRepository.save(newClient("client456", "99999999000100", PlanType.PREPAID, "3.00", "0.00", true));

        resetJob.run();

        var renewed = clientRepository.findById("client123").orElseThrow();
        assertThat(renewed.getLimit()).isEqualByComparingTo("50.00");
        assertThat(renewed.getLimitPeriod()).isEqualTo(currentPeriod());
        assertThat(balanceOf("client456")).isEqualByComparingTo("3.00");
    }

    // ---------- Cliente invativo e concorrência ---------

    @Test
    void shouldRejectInactiveClientWithoutCharging() throws Exception {
        clientRepository.save(newClient("client123", "12345678000199", PlanType.PREPAID, "10.00", "0.00", false));

        send("inativo").andExpect(status().isForbidden());

        assertThat(balanceOf("client123")).isEqualByComparingTo("10.00");
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void shouldNeverOverspendUnderConcurrentRequests() throws Exception {
        seed(PlanType.PREPAID, "1.00", "0.00");   // dá para 4 mensagens normais
        var authorization = bearer();
        int requests = 8;

        try(var executor = Executors.newFixedThreadPool(requests)) {
            var start = new CountDownLatch(1);

            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/messages")
                                    .header("Authorization", authorization)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(validBody("concorrente")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            executor.shutdown();

            assertThat(statuses).filteredOn(s -> s == 202).hasSize(4);
            assertThat(statuses).filteredOn(s -> s == 402).hasSize(4);
            assertThat(balanceOf("client123")).isEqualByComparingTo("0.00");
            assertThat(messageRepository.count()).isEqualTo(4);
            assertThat(queue.size()).isEqualTo(4);
        }
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
                "client123",
                "user789",
                "conteúdo",
                timestamp,
                MessagePriority.NORMAL,
                status,
                new BigDecimal("0.25"));
    }

    private LocalDate currentPeriod() {
        return LocalDate.now(clock).withDayOfMonth(1);
    }

    private Client newClient(String id, String document, PlanType plan,
                             String balance, String limit, boolean active) {
        return new Client(id, "Empresa ABC", "hash-irrelevante", document, DocumentType.CNPJ,
                plan, new BigDecimal(balance), new BigDecimal(limit), active);
    }

    private void seed(PlanType plan, String balance, String limit) {
        clientRepository.save(newClient("client123", "12345678000199", plan, balance, limit, true));
    }

    private BigDecimal balanceOf(String id) {
        return clientRepository.findById(id).orElseThrow().getBalance();
    }

    private BigDecimal limitOf(String id) {
        return clientRepository.findById(id).orElseThrow().getLimit();
    }

    private ResultActions send(String content) throws Exception {
        return mockMvc.perform(post("/messages")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(content)));
    }
}