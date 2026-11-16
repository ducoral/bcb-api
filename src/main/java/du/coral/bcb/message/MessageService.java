package du.coral.bcb.message;

import du.coral.bcb.client.ClientRepository;
import du.coral.bcb.shared.error.InactiveClientException;
import du.coral.bcb.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ClientRepository clientRepository;
    private final MessageQueue queue;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MessageService(MessageRepository messageRepository, ClientRepository clientRepository,
                          MessageQueue queue, TransactionTemplate transactionTemplate, Clock clock) {
        this.messageRepository = messageRepository;
        this.clientRepository = clientRepository;
        this.queue = queue;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Message send(String senderId, String recipientId, String content, MessagePriority priority) {
        var effective = priority != null
                ? priority
                : MessagePriority.NORMAL;
        var period = LocalDate.now(clock).withDayOfMonth(1);

        // Débito + gravação na mesma transação; qualquer falha desfaz os dois.
        var message = transactionTemplate.execute(status -> {
            var client = clientRepository
                    .findWithLockById(senderId)
                    .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + senderId));
            if (!client.isActive()) {
                throw new InactiveClientException();
            }
            client.charge(effective.cost(), period);
            return messageRepository.save(
                    Message.createQueued(senderId, recipientId, content, effective));
        });

        // Só depois do commit: nunca há mensagem na fila sem cobrança confirmada.
        queue.enqueue(message);
        return message;
    }
}