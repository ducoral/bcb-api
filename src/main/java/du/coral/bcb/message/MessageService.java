package du.coral.bcb.message;

import du.coral.bcb.client.ClientRepository;
import du.coral.bcb.shared.error.InactiveClientException;
import du.coral.bcb.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
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

    @Transactional(readOnly = true)
    public Page<Message> search(String senderId, MessageStatus status, MessagePriority priority,
                                String recipientId, Instant from, Instant to, int page, int size) {
        Specification<Message> spec = (root, _, cb) -> cb.equal(root.get("senderId"), senderId);

        if (status != null)
            spec = spec.and((root, _, cb) ->
                    cb.equal(root.get("status"), status));

        if (priority != null)
            spec = spec.and((root, _, cb) ->
                    cb.equal(root.get("priority"), priority));

        if (recipientId != null && !recipientId.isBlank())
            spec = spec.and((root, _, cb) ->
                    cb.equal(root.get("recipientId"), recipientId));

        if (from != null)
            spec = spec.and((root, _, cb) ->
                    cb.greaterThanOrEqualTo(root.<Instant>get("timestamp"), from));

        if (to != null)
            spec = spec.and((root, _, cb) ->
                    cb.lessThanOrEqualTo(root.<Instant>get("timestamp"), to));

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.asc("id")));

        return messageRepository.findAll(spec, pageable);
    }

    /** Só devolve a mensagem se ela pertencer ao cliente informado. */
    @Transactional(readOnly = true)
    public Message findOwned(String id, String clientId) {
        return messageRepository.findByIdAndSenderId(id, clientId)
                .orElseThrow(() -> new NotFoundException("Mensagem não encontrada: " + id));
    }
}