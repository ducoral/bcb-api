package du.coral.bcb.message;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "message")
public class Message implements Persistable<String> {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "sender_id", nullable = false, length = 64)
    private String senderId;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessagePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MessageStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    @Transient
    private boolean isNew = true;

    protected Message() {
    }

    public Message(String id,
                   String senderId,
                   String recipientId,
                   String content,
                   Instant timestamp,
                   MessagePriority priority,
                   MessageStatus status,
                   BigDecimal cost) {
        this.id = id;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
        this.timestamp = timestamp;
        this.priority = priority;
        this.status = status;
        this.cost = cost;
    }

    public static Message createQueued(
            String senderId,
            String recipientId,
            String content,
            MessagePriority priority) {
        return new Message(
                "msg-%s".formatted(UUID.randomUUID()),
                senderId,
                recipientId,
                content,
                Instant.now().truncatedTo(ChronoUnit.MICROS), // mesma precisão do PostgreSQL
                priority,
                MessageStatus.QUEUED,
                priority.cost());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getContent() {
        return content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public MessagePriority getPriority() {
        return priority;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public BigDecimal getCost() {
        return cost;
    }

    @Override
    public String toString() {
        return "%s [%s]".formatted(content, priority);
    }
}