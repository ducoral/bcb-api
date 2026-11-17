package du.coral.bcb.conversation;

import com.jayway.jsonpath.JsonPath;
import du.coral.bcb.auth.JwtAuthFilter;
import du.coral.bcb.auth.JwtService;
import du.coral.bcb.auth.TokenStore;
import du.coral.bcb.client.*;
import du.coral.bcb.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123",
        "app.conversation-worker.enabled=false"})
@Testcontainers
class ConversationIT {

    static final Instant BASE = Instant.parse("2026-11-15T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired WebApplicationContext context;
    @Autowired ClientRepository clientRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired ConversationMessageRepository conversationMessageRepository;
    @Autowired MessageQueue queue;
    @Autowired ConversationFeeder feeder;
    @Autowired JwtService jwtService;
    @Autowired TokenStore tokenStore;
    @Autowired JwtAuthFilter jwtAuthFilter;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(jwtAuthFilter)
                .build();
        conversationMessageRepository.deleteAll();   // ordem por causa das FKs
        conversationRepository.deleteAll();
        messageRepository.deleteAll();
        clientRepository.deleteAll();
        clientRepository.save(newClient("client123", "12345678000199"));
        queue.clear();
    }

    // ---------- Worker (ConversationFeeder) ----------

    @Test
    void shouldCreateConversationAndMessageFromQueuedMessage() throws Exception {
        var messageId = postMessage("user789", "Olá, como vai?");

        assertThat(feeder.drain()).isEqualTo(1);

        var message = messageRepository.findById(messageId).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);

        var conversation = conversationRepository
                .findByClientIdAndRecipientId("client123", "user789").orElseThrow();
        assertThat(conversation.getId()).startsWith("conv-");
        assertThat(conversation.getLastMessageContent()).isEqualTo("Olá, como vai?");
        assertThat(conversation.getLastMessageTime()).isEqualTo(message.getTimestamp());
        assertThat(conversation.getUnreadCount()).isEqualTo(1);

        var stored = conversationMessageRepository.findById(messageId).orElseThrow();
        assertThat(stored.getConversationId()).isEqualTo(conversation.getId());
        assertThat(stored.getSenderId()).isEqualTo("client123");
        assertThat(stored.getRecipientId()).isEqualTo("user789");
        assertThat(stored.getContent()).isEqualTo("Olá, como vai?");
        assertThat(stored.getTimestamp()).isEqualTo(message.getTimestamp());
        assertThat(queue.size()).isZero();
    }

    @Test
    void shouldReuseConversationAndUpdateLastMessage() throws Exception {
        postMessage("user789", "primeira");
        feeder.drain();
        postMessage("user789", "segunda");
        feeder.drain();

        assertThat(conversationRepository.count()).isEqualTo(1);
        var conversation = conversationRepository.findAll().getFirst();
        assertThat(conversation.getLastMessageContent()).isEqualTo("segunda");
        assertThat(conversation.getUnreadCount()).isEqualTo(2);
        assertThat(conversationMessageRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldCreateSeparateConversationsPerRecipient() throws Exception {
        postMessage("user111", "para o 111");
        postMessage("user222", "para o 222");

        assertThat(feeder.drain()).isEqualTo(2);

        assertThat(conversationRepository.count()).isEqualTo(2);
        assertThat(conversationRepository.findByClientIdAndRecipientId("client123", "user111")).isPresent();
        assertThat(conversationRepository.findByClientIdAndRecipientId("client123", "user222")).isPresent();
    }

    @Test
    void shouldNotProcessTheSameMessageTwice() {
        var message = messageRepository.save(queuedMessage("msg-1", BASE, "oi"));
        queue.enqueue(message);
        assertThat(feeder.drain()).isEqualTo(1);

        queue.enqueue(message);   // ex.: recarregada do banco depois de um reinício
        assertThat(feeder.drain()).isZero();

        assertThat(conversationMessageRepository.count()).isEqualTo(1);
        assertThat(conversationRepository.findAll().getFirst().getUnreadCount()).isEqualTo(1);
    }

    @Test
    void shouldKeepNewestLastMessageWhenAnOlderOneArrivesLater() {
        var newer = messageRepository.save(queuedMessage("msg-new", BASE.plusSeconds(60), "mais nova"));
        var older = messageRepository.save(queuedMessage("msg-old", BASE, "mais antiga"));

        queue.enqueue(newer);
        feeder.drain();
        queue.enqueue(older);
        feeder.drain();

        var conversation = conversationRepository.findAll().getFirst();
        assertThat(conversation.getLastMessageContent()).isEqualTo("mais nova");
        assertThat(conversation.getLastMessageTime()).isEqualTo(newer.getTimestamp());
        assertThat(conversation.getUnreadCount()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreQueuedMessageThatIsMissingFromDatabase() {
        queue.enqueue(new Message("msg-ghost", "client123", "user789", "x", BASE,
                MessagePriority.NORMAL, MessageStatus.QUEUED, MessagePriority.NORMAL.cost()));

        assertThat(feeder.drain()).isZero();
        assertThat(conversationRepository.count()).isZero();
    }

    // ---------- GET /conversations ----------

    @Test
    void shouldListOnlyOwnConversationsNewestFirst() throws Exception {
        seedOtherClient();
        conversationRepository.saveAll(List.of(
                conversation("conv-1", "client123", "user111", BASE, 0),
                conversation("conv-2", "client123", "user222", BASE.plusSeconds(60), 2),
                conversation("conv-other", "client999", "user111", BASE.plusSeconds(120), 0)));

        mockMvc.perform(authed(get("/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value("conv-2"))
                .andExpect(jsonPath("$.items[1].id").value("conv-1"))
                .andExpect(jsonPath("$.items[*].clientId", everyItem(is("client123"))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldReturnEmptyPageWhenThereAreNoConversations() throws Exception {
        mockMvc.perform(authed(get("/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldPaginateConversations() throws Exception {
        for (int i = 1; i <= 5; i++) {
            conversationRepository.save(conversation("conv-" + i, "client123", "user" + i,
                    BASE.plusSeconds(i), 0));
        }

        // mais novas primeiro: conv-5, conv-4 | conv-3, conv-2 | conv-1
        mockMvc.perform(authed(get("/conversations").param("page", "1").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value("conv-3"))
                .andExpect(jsonPath("$.items[1].id").value("conv-2"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // ---------- GET /conversations/{id} ----------

    @Test
    void shouldReturnConversationDetails() throws Exception {
        conversationRepository.save(conversation("conv-1", "client123", "user789", BASE, 3));

        mockMvc.perform(authed(get("/conversations/conv-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conv-1"))
                .andExpect(jsonPath("$.clientId").value("client123"))
                .andExpect(jsonPath("$.recipientId").value("user789"))
                .andExpect(jsonPath("$.lastMessageContent").value("Olá, como vai?"))
                .andExpect(jsonPath("$.lastMessageTime", startsWith("2026-11-15T10:00:00")))
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void shouldReturnNotFoundForUnknownConversation() throws Exception {
        mockMvc.perform(authed(get("/conversations/inexistente")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForConversationOfAnotherClient() throws Exception {
        seedOtherClient();
        conversationRepository.save(conversation("conv-other", "client999", "user789", BASE, 0));

        mockMvc.perform(authed(get("/conversations/conv-other")))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /conversations/{id}/messages ----------

    @Test
    void shouldListConversationMessagesNewestFirst() throws Exception {
        conversationRepository.save(conversation("conv-1", "client123", "user789", BASE, 3));
        conversationMessageRepository.saveAll(List.of(
                conversationMessage("msg-1", "conv-1", BASE),
                conversationMessage("msg-2", "conv-1", BASE.plusSeconds(60)),
                conversationMessage("msg-3", "conv-1", BASE.plusSeconds(120))));

        mockMvc.perform(authed(get("/conversations/conv-1/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id").value("msg-3"))
                .andExpect(jsonPath("$.items[2].id").value("msg-1"))
                .andExpect(jsonPath("$.items[0].conversationId").value("conv-1"))
                .andExpect(jsonPath("$.items[0].senderId").value("client123"))
                .andExpect(jsonPath("$.items[0].recipientId").value("user789"))
                .andExpect(jsonPath("$.items[0].content").value("conteúdo msg-3"))
                .andExpect(jsonPath("$.items[0].timestamp", startsWith("2026-11-15T10:02:00")))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldNotMixMessagesOfDifferentConversations() throws Exception {
        conversationRepository.saveAll(List.of(
                conversation("conv-1", "client123", "user111", BASE, 0),
                conversation("conv-2", "client123", "user222", BASE, 0)));
        conversationMessageRepository.saveAll(List.of(
                conversationMessage("msg-a", "conv-1", BASE),
                conversationMessage("msg-b", "conv-2", BASE)));

        mockMvc.perform(authed(get("/conversations/conv-1/messages")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value("msg-a"));
    }

    @Test
    void shouldPaginateConversationMessages() throws Exception {
        conversationRepository.save(conversation("conv-1", "client123", "user789", BASE, 0));
        for (int i = 1; i <= 5; i++) {
            conversationMessageRepository.save(
                    conversationMessage("msg-" + i, "conv-1", BASE.plusSeconds(i)));
        }

        mockMvc.perform(authed(get("/conversations/conv-1/messages")
                        .param("page", "1").param("size", "2")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value("msg-3"))
                .andExpect(jsonPath("$.items[1].id").value("msg-2"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void shouldReturnNotFoundForMessagesOfUnknownConversation() throws Exception {
        mockMvc.perform(authed(get("/conversations/inexistente/messages")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForMessagesOfAnotherClientsConversation() throws Exception {
        seedOtherClient();
        conversationRepository.save(conversation("conv-other", "client999", "user789", BASE, 0));
        conversationMessageRepository.save(conversationMessage("msg-other", "conv-other", BASE));

        mockMvc.perform(authed(get("/conversations/conv-other/messages")))
                .andExpect(status().isNotFound());
    }

    // ---------- Autenticação e validação ----------

    @ParameterizedTest
    @ValueSource(strings = {"/conversations", "/conversations/conv-1", "/conversations/conv-1/messages"})
    void shouldRejectReadEndpointsWithoutToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/conversations?size=0", "/conversations?size=101", "/conversations?page=-1",
            "/conversations/conv-1/messages?size=0", "/conversations/conv-1/messages?size=101",
            "/conversations/conv-1/messages?page=-1"})
    void shouldReturnBadRequestForInvalidPagination(String url) throws Exception {
        mockMvc.perform(authed(get(url)))
                .andExpect(status().isBadRequest());
    }

    // ---------- Fluxo completo ----------

    @Test
    void shouldExposeFedConversationThroughTheApi() throws Exception {
        var messageId = postMessage("user789", "Olá, como vai?");
        feeder.drain();

        var list = mockMvc.perform(authed(get("/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].recipientId").value("user789"))
                .andExpect(jsonPath("$.items[0].lastMessageContent").value("Olá, como vai?"))
                .andExpect(jsonPath("$.items[0].unreadCount").value(1))
                .andReturn().getResponse().getContentAsString();
        var conversationId = JsonPath.read(list, "$.items[0].id");

        mockMvc.perform(authed(get("/conversations/" + conversationId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(messageId))
                .andExpect(jsonPath("$.items[0].content").value("Olá, como vai?"));

        mockMvc.perform(authed(get("/messages/" + messageId + "/status")))
                .andExpect(jsonPath("$.status").value("sent"));
    }

    // ---------- helpers ----------

    private String bearer() {
        var token = jwtService.issue("client123");
        tokenStore.save(token);
        return "Bearer " + token.value();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer());
    }

    private String postMessage(String recipientId, String content) throws Exception {
        var body = """
                {"recipientId": "%s", "content": "%s"}
                """.formatted(recipientId, content);
        var response = mockMvc.perform(post("/messages")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private Client newClient(String id, String document) {
        return new Client(id, "Empresa ABC", "hash-irrelevante", document, DocumentType.CNPJ,
                PlanType.PREPAID, new BigDecimal("10.00"), BigDecimal.ZERO, true);
    }

    private void seedOtherClient() {
        clientRepository.save(newClient("client999", "99999999000100"));
    }

    private Message queuedMessage(String id, Instant timestamp, String content) {
        return new Message(id, "client123", "user789", content, timestamp,
                MessagePriority.NORMAL, MessageStatus.QUEUED, MessagePriority.NORMAL.cost());
    }

    private Conversation conversation(String id, String clientId, String recipientId,
                                      Instant lastMessageTime, int unreadCount) {
        return new Conversation(id, clientId, recipientId,
                "Olá, como vai?", lastMessageTime, unreadCount);
    }

    private ConversationMessage conversationMessage(String id, String conversationId, Instant timestamp) {
        return new ConversationMessage(id, conversationId, "client123", "user789",
                "conteúdo " + id, timestamp);
    }
}