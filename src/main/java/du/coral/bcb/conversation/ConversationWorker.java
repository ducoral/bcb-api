package du.coral.bcb.conversation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.conversation-worker.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationWorker {

    private final ConversationFeeder feeder;

    public ConversationWorker(ConversationFeeder feeder) {
        this.feeder = feeder;
    }

    /** fixedDelay: a próxima execução só começa depois que a anterior terminou (sem sobreposição). */
    @Scheduled(initialDelayString = "PT1S",
               fixedDelayString = "${app.conversation-worker.interval:PT0.5S}")
    public void run() {
        feeder.drain();
    }
}