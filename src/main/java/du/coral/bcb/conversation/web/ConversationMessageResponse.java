package du.coral.bcb.conversation.web;

import du.coral.bcb.conversation.ConversationMessage;

import java.time.Instant;

public record ConversationMessageResponse(
        String id, String conversationId, String senderId, String recipientId,
        String content, Instant timestamp
) {
    public static ConversationMessageResponse from(ConversationMessage m) {
        return new ConversationMessageResponse(
                m.getId(), m.getConversationId(), m.getSenderId(),
                m.getRecipientId(), m.getContent(), m.getTimestamp());
    }
}