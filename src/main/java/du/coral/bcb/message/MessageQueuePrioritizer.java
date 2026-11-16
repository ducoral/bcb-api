package du.coral.bcb.message;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageQueuePrioritizer {

    private final Map<MessagePriority, PriorizationQueue> queueMap = new ConcurrentHashMap<>();

    private final List<MessagePriority> priorizationCycle = new ArrayList<>();

    private int currentCycleIndex = 0;

    public int size() {
        return queueMap.values()
                .stream()
                .map(PriorizationQueue::queue)
                .map(Queue::size)
                .reduce(0, Integer::sum);
    }

    public void clear() {
        queueMap.values()
                .forEach(PriorizationQueue::clear);
    }

    public boolean offer(Message message) {
        var priority = message.getPriority();

        if (!priorizationCycle.contains(priority))
            priorizationCycle.add(priority);

        return queueMap
                .computeIfAbsent(priority, _ -> PriorizationQueue.of(priority))
                .offer(message);
    }

    public Message poll() {
        if (queueMap.isEmpty() || isEmpty())
            return null;

        restartCycleIfCompleted();

        return nextPriorizationQueue().pool();
    }

    private PriorizationQueue nextPriorizationQueue() {
        var current = getCurrentPriorizationQueue();
        while (current.isEmptyOrCountAtZero()) {
           doNextCycleIndex();
           current = getCurrentPriorizationQueue();
        }
        doNextCycleIndex();
        return current;
    }

    private PriorizationQueue getCurrentPriorizationQueue() {
        var priority = priorizationCycle.get(currentCycleIndex);
        return queueMap.get(priority);
    }

    private void doNextCycleIndex() {
        currentCycleIndex = (currentCycleIndex + 1) % priorizationCycle.size();
    }

    public boolean isEmpty() {
        return queueMap.values()
                .stream()
                .map(PriorizationQueue::queue)
                .allMatch(Collection::isEmpty);
    }

    private void restartCycleIfCompleted() {
        var isTheCycleComplete = queueMap.values()
                .stream()
                .map(PriorizationQueue::count)
                .allMatch(count -> count.get() == 0);

        if (isTheCycleComplete) {
            queueMap
                    .values()
                    .forEach(PriorizationQueue::resetCount);
            currentCycleIndex = 0;
        }
    }

    record PriorizationQueue(MessagePriority priority, AtomicInteger count, Queue<Message> queue) {

        static PriorizationQueue of(MessagePriority priority) {
            return new PriorizationQueue(
                    priority,
                    new AtomicInteger(priority.weight()),
                    new ConcurrentLinkedQueue<>());
        }

        void clear() {
            resetCount();
            queue.clear();
        }

        boolean offer(Message message) {
            return queue.offer(message);
        }

        boolean isEmptyOrCountAtZero() {
            return isQueueEmpty()
                    || isTheCountAtZero();
        }

        boolean isQueueEmpty() {
            return queue.isEmpty();
        }

        boolean isTheCountAtZero() {
            return count.get() == 0;
        }

        void resetCount() {
            count.set(priority.weight());
        }

        Message pool() {
            count.decrementAndGet();
            return queue.poll();
        }
    }
}