package du.coral.bcb.conversation;

import com.jayway.jsonpath.JsonPath;
import du.coral.bcb.auth.JwtAuthFilter;
import du.coral.bcb.auth.JwtService;
import du.coral.bcb.auth.TokenStore;
import du.coral.bcb.client.Client;
import du.coral.bcb.client.ClientRepository;
import du.coral.bcb.client.DocumentType;
import du.coral.bcb.client.PlanType;
import du.coral.bcb.message.MessageQueue;
import du.coral.bcb.message.MessageRepository;
import du.coral.bcb.message.MessageStatus;
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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123",
        "app.conversation-worker.interval=PT0.1S"})
@Testcontainers
class ConversationWorkerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired WebApplicationContext context;
    @Autowired ClientRepository clientRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired ConversationMessageRepository conversationMessageRepository;
    @Autowired MessageQueue queue;
    @Autowired JwtService jwtService;
    @Autowired TokenStore tokenStore;
    @Autowired JwtAuthFilter jwtAuthFilter;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(jwtAuthFilter).build();
        conversationMessageRepository.deleteAll();
        conversationRepository.deleteAll();
        messageRepository.deleteAll();
        clientRepository.deleteAll();
        clientRepository.save(new Client("client123", "Empresa ABC", "hash-irrelevante",
                "12345678000199", DocumentType.CNPJ, PlanType.PREPAID,
                new BigDecimal("10.00"), BigDecimal.ZERO, true));
        queue.clear();
    }

    @Test
    void shouldFeedConversationInTheBackground() throws Exception {
        var messageId = postMessage("user789", "Olá, como vai?");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(conversationRepository
                    .findByClientIdAndRecipientId("client123", "user789")).isPresent();
            assertThat(messageRepository.findById(messageId).orElseThrow().getStatus())
                    .isEqualTo(MessageStatus.SENT);
        });

        assertThat(conversationMessageRepository.existsById(messageId)).isTrue();
        assertThat(queue.size()).isZero();
    }

    private String postMessage(String recipientId, String content) throws Exception {
        JwtService.IssuedToken token = jwtService.issue("client123");
        tokenStore.save(token);
        String response = mockMvc.perform(post("/messages")
                        .header("Authorization", "Bearer " + token.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId": "%s", "content": "%s"}
                                """.formatted(recipientId, content)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}