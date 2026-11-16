package du.coral.bcb.message;

import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private final MessageRepository repository;
    private final MessageQueue queue;

    public MessageService(MessageRepository repository, MessageQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    /**
     * Sem @Transactional de propósito: o save() confirma a própria transação ao retornar,
     * e só então a mensagem entra na fila. Com uma transação externa, a mensagem poderia
     * ser enfileirada e depois sofrer rollback.
     */
    public Message send(String senderId, String conversationId, String recipientId,
                        String content, MessagePriority priority) {
        var message = repository.save(
                Message.createQueued(
                        conversationId,
                        senderId,
                        recipientId,
                        content,
                        priority != null ? priority : MessagePriority.NORMAL));
        queue.enqueue(message);
        return message;
    }
}