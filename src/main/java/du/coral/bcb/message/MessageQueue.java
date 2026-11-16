package du.coral.bcb.message;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageQueue {

    private final Set<String> queuedIds = ConcurrentHashMap.newKeySet();
    private final MessageQueuePrioritizer queuePrioritizer = new MessageQueuePrioritizer();

    public boolean enqueue(Message message) {
        var isAdded = queuedIds.add(message.getId());
        if (!isAdded)
            return false;
        return queuePrioritizer.offer(message);
    }

    public int enqueueAll(Collection<Message> messages) {
        int added = 0;
        for (var message : messages)
            if (enqueue(message))
                added++;
        return added;
    }

    public Optional<Message> poll() {
        var message = queuePrioritizer.poll();
        if (message != null) {
            queuedIds.remove(message.getId());
        }
        return Optional.ofNullable(message);
    }

    public int size() {
        return queuePrioritizer.size();
    }

    public void clear() {
        queuePrioritizer.clear();
        queuedIds.clear();
    }
}