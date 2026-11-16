package du.coral.bcb.message;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class MessageQueue {

    private final Set<String> queuedIds = ConcurrentHashMap.newKeySet();
    private final Queue<Message> queue = new ConcurrentLinkedQueue<>();

    public boolean enqueue(Message message) {
        var isAdded = queuedIds.add(message.getId());
        if (!isAdded)
            return false;
        return queue.offer(message);
    }

    public int enqueueAll(Collection<Message> messages) {
        int added = 0;
        for (var message : messages)
            if (enqueue(message))
                added++;
        return added;
    }

    public Optional<Message> poll() {
        var message = queue.poll();
        if (message != null) {
            queuedIds.remove(message.getId());
        }
        return Optional.ofNullable(message);
    }

    public List<Message> snapshot() {
        return List.copyOf(queue);
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        queuedIds.clear();
    }
}