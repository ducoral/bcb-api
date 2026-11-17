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

    /** Dados de uma mensagem processada, usados para o log depois do commit. */
    private record Fed(String conversationId, String clientId, String recipientId,
                       boolean conversationCreated, int unreadCount) {}

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

    /** Consome toda a fila (FIFO) e devolve quantas mensagens foram processadas com sucesso. */
    public int drain() {
        long drainStart = System.nanoTime();
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        Optional<Message> next;
        while ((next = queue.poll()).isPresent()) {
            var messageId = next.get().getId();
            long start = System.nanoTime();
            log.info("Mensagem {} retirada da fila ({} restante(s))", messageId, queue.size());

            try {
                Fed fed = transactionTemplate.execute(status -> feed(messageId));
                if (fed == null) {
                    skipped++;
                    continue;
                }
                processed++;

                if (fed.conversationCreated()) {
                    log.info("Conversa {} criada: cliente={}, destinatario={}",
                            fed.conversationId(), fed.clientId(), fed.recipientId());
                } else {
                    log.info("Conversa {} atualizada: cliente={}, destinatario={}, naoLidas={}",
                            fed.conversationId(), fed.clientId(), fed.recipientId(), fed.unreadCount());
                }
                log.info("Mensagem {} registrada na conversa {} e marcada como sent ({} ms)",
                        messageId, fed.conversationId(), elapsedMillis(start));
            } catch (RuntimeException e) {
                failed++;
                log.error("Falha ao processar a mensagem {}", messageId, e);
                markFailed(messageId);
            }
        }

        if (processed + skipped + failed > 0) {
            log.info("Consumo da fila finalizado: processadas={}, ignoradas={}, falhas={}, restantes={}, tempo={} ms",
                    processed, skipped, failed, queue.size(), elapsedMillis(drainStart));
        }
        return processed;
    }

    /** Uma transação por mensagem: conversa, mensagem da conversa e status mudam juntos. */
    private Fed feed(String messageId) {
        var message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            log.warn("Mensagem {} não existe mais no banco; ignorada", messageId);
            return null;
        }
        if (message.getStatus() != MessageStatus.QUEUED) {
            log.info("Mensagem {} ignorada: status atual é {} (já processada?)",
                    messageId, message.getStatus().value());
            return null;
        }

        var existing = conversationRepository
                .findByClientIdAndRecipientId(message.getSenderId(), message.getRecipientId());
        var created = existing.isEmpty();

        var conversation = existing.orElseGet(() -> Conversation.startedBy(message));
        conversation.registerMessage(message.getContent(), message.getTimestamp());
        conversation = conversationRepository.save(conversation);

        conversationMessageRepository.save(new ConversationMessage(
                message.getId(), conversation.getId(), message.getSenderId(),
                message.getRecipientId(), message.getContent(), message.getTimestamp()));

        message.changeStatus(MessageStatus.SENT);
        return new Fed(conversation.getId(), conversation.getClientId(),
                conversation.getRecipientId(), created, conversation.getUnreadCount());
    }

    private void markFailed(String messageId) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    messageRepository.findById(messageId)
                            .filter(m -> m.getStatus() == MessageStatus.QUEUED)
                            .ifPresent(m -> {
                                m.changeStatus(MessageStatus.FAILED);
                                log.info("Mensagem {} marcada como failed", messageId);
                            }));
        } catch (RuntimeException e) {
            log.error("Não foi possível marcar a mensagem {} como failed", messageId, e);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}