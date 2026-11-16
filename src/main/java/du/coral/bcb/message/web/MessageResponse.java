package du.coral.bcb.message.web;

import du.coral.bcb.message.Message;
import du.coral.bcb.message.MessagePriority;
import du.coral.bcb.message.MessageStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MessageResponse(
        String id,
        String conversationId,
        String senderId,
        String recipientId,
        String content,
        Instant timestamp,
        MessagePriority priority,
        MessageStatus status,
        BigDecimal cost
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getRecipientId(),
                m.getContent(),
                m.getTimestamp(),
                m.getPriority(),
                m.getStatus(),
                m.getCost());
    }
}