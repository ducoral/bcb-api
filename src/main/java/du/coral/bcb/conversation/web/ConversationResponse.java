package du.coral.bcb.conversation.web;

import du.coral.bcb.conversation.Conversation;

import java.time.Instant;

public record ConversationResponse(
        String id, String clientId, String recipientId, String lastMessageContent,
        Instant lastMessageTime, int unreadCount
) {
    public static ConversationResponse from(Conversation c) {
        return new ConversationResponse(c.getId(), c.getClientId(), c.getRecipientId(),
                c.getLastMessageContent(), c.getLastMessageTime(), c.getUnreadCount());
    }
}