package du.coral.bcb.conversation;

import du.coral.bcb.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class ConversationFeeder {

    private static final Logger log = LoggerFactory.getLogger(ConversationFeeder.class);

    private final MessageQueue queue;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final TransactionTemplate transactionTemplate;

    public ConversationFeeder(MessageQueue queue, MessageRepository messageRepository,
                              ConversationRepository conversationRepository,
                              ConversationMessageRepository conversationMessageRepository,
                              TransactionTemplate transactionTemplate) {
        this.queue = queue;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Consome toda a fila (FIFO) e devolve quantas mensagens foram processadas com sucesso.
     */
    public int drain() {
        var processed = 0;
        Optional<Message> next;
        while ((next = queue.poll()).isPresent()) {
            var messageId = next.get().getId();
            try {
                var isProcessed = transactionTemplate
                        .execute(status -> feed(messageId));
                if (isProcessed)
                    processed++;
            } catch (RuntimeException e) {
                log.error("Falha ao processar a mensagem {}", messageId, e);
                markFailed(messageId);
            }
        }
        return processed;
    }

    /**
     * Uma transação por mensagem: conversa, mensagem da conversa e status mudam juntos.
     */
    private boolean feed(String messageId) {
        var message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            log.warn("Mensagem {} não existe mais no banco; ignorada", messageId);
            return false;
        }
        if (message.getStatus() != MessageStatus.QUEUED) {
            return false; // já processada (ex.: recarregada do banco depois de um reinício)
        }

        var conversation = conversationRepository
                .findByClientIdAndRecipientId(message.getSenderId(), message.getRecipientId())
                .orElseGet(() -> Conversation.startedBy(message));
        conversation.registerMessage(message.getContent(), message.getTimestamp());
        conversation = conversationRepository.save(conversation);

        conversationMessageRepository.save(new ConversationMessage(
                message.getId(), conversation.getId(), message.getSenderId(),
                message.getRecipientId(), message.getContent(), message.getTimestamp()));

        message.changeStatus(MessageStatus.SENT);
        return true;
    }

    private void markFailed(String messageId) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    messageRepository.findById(messageId)
                            .filter(m -> m.getStatus() == MessageStatus.QUEUED)
                            .ifPresent(m -> m.changeStatus(MessageStatus.FAILED)));
        } catch (RuntimeException e) {
            log.error("Não foi possível marcar a mensagem {} como failed", messageId, e);
        }
    }
}