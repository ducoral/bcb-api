package du.coral.bcb.conversation;

import du.coral.bcb.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;

    public ConversationService(ConversationRepository conversations,
                               ConversationMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public Page<Conversation> list(String clientId, int page, int size) {
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("lastMessageTime"), Sort.Order.asc("id")));
        return conversations.findByClientId(clientId, pageable);
    }

    @Transactional(readOnly = true)
    public Conversation get(String id, String clientId) {
        return conversations.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new NotFoundException("Conversa não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ConversationMessage> messages(String id, String clientId, int page, int size) {
        get(id, clientId); // 404 se não existir ou for de outro cliente
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.asc("id")));
        return messages.findByConversationId(id, pageable);
    }
}