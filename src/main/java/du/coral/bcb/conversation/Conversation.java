package du.coral.bcb.conversation;

import du.coral.bcb.message.Message;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Column(name = "last_message_content", nullable = false, length = 4000)
    private String lastMessageContent;

    @Column(name = "last_message_time", nullable = false)
    private Instant lastMessageTime;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    protected Conversation() {
    }

    public Conversation(String id, String clientId, String recipientId,
                        String lastMessageContent, Instant lastMessageTime, int unreadCount) {
        this.id = id;
        this.clientId = clientId;
        this.recipientId = recipientId;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageTime = lastMessageTime;
        this.unreadCount = unreadCount;
    }

    /**
     * Conversa nova, ainda sem a mensagem registrada (unreadCount = 0).
     */
    public static Conversation startedBy(Message message) {
        return new Conversation("conv-" + UUID.randomUUID(), message.getSenderId(),
                message.getRecipientId(), message.getContent(), message.getTimestamp(), 0);
    }

    /**
     * Atualiza a última mensagem (só se não for mais antiga que a atual) e soma 1 ao contador.
     */
    public void registerMessage(String content, Instant timestamp) {
        if (!timestamp.isBefore(lastMessageTime)) {
            lastMessageContent = content;
            lastMessageTime = timestamp;
        }
        unreadCount++;
    }

    public String getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public Instant getLastMessageTime() {
        return lastMessageTime;
    }

    public int getUnreadCount() {
        return unreadCount;
    }
}