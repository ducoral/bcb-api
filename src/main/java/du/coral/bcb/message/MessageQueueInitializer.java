package du.coral.bcb.message;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MessageQueueInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MessageQueueInitializer.class);

    private final MessageRepository repository;
    private final MessageQueue queue;

    public MessageQueueInitializer(MessageRepository repository, MessageQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        load();
    }

    /** Carrega as mensagens `queued` (mais antigas primeiro). Pode ser chamado de novo sem duplicar. */
    public int load() {
        var pending = repository.findByStatusOrderByTimestampAsc(MessageStatus.QUEUED);
        var added = queue.enqueueAll(pending);
        log.info("{} mensagem(ns) com status queued carregada(s) na fila", added);
        return added;
    }
}