package du.coral.bcb.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.conversation-worker.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationWorker {

    private static final Logger log = LoggerFactory.getLogger(ConversationWorker.class);

    private final ConversationFeeder feeder;

    public ConversationWorker(ConversationFeeder feeder,
                              @Value("${app.conversation-worker.interval:PT0.5S}") Duration interval) {
        this.feeder = feeder;
        log.info("Worker de conversas ativo (intervalo entre execuções: {} ms)", interval.toMillis());
    }

    @Scheduled(initialDelayString = "PT1S",
            fixedDelayString = "${app.conversation-worker.interval:PT0.5S}")
    public void run() {
        feeder.drain();
    }
}