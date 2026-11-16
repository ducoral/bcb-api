package du.coral.bcb.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static du.coral.bcb.message.MessagePriority.NORMAL;
import static du.coral.bcb.message.MessagePriority.URGENT;
import static org.junit.jupiter.api.Assertions.*;

class MessageQueuePrioritizerTest {

    @Test
    void sholdReturnNullWhenQueueEmpty() {
        var queue = new MessageQueuePrioritizer();
        assertTrue(queue.isEmpty());
        assertNull(queue.poll());
    }

    @Test
    void sholdReturnOnlyNormalPriority() {
        var queue = new MessageQueuePrioritizer();
        queue.offer(message("A", NORMAL));
        queue.offer(message("B", NORMAL));
        queue.offer(message("B", NORMAL));

        assertEquals(3, queue.size());

        assertEquals("A", queue.poll().getContent());
        assertEquals("B", queue.poll().getContent());
        assertEquals("B", queue.poll().getContent());

        assertTrue(queue.isEmpty());
    }

    @Test
    void sholdReturnOnlyUrgentPriority() {
        var queue = new MessageQueuePrioritizer();
        queue.offer(message("A", URGENT));
        queue.offer(message("B", URGENT));
        queue.offer(message("C", URGENT));

        assertEquals(3, queue.size());

        assertEquals("A", queue.poll().getContent());
        assertEquals("B", queue.poll().getContent());
        assertEquals("C", queue.poll().getContent());

        assertTrue(queue.isEmpty());
    }

    @Test
    void sholdReturnPrioritizedMessages() {
        var queue = new MessageQueuePrioritizer();
        queue.offer(message("NORMAL_1", NORMAL));
        queue.offer(message("NORMAL_2", NORMAL));
        queue.offer(message("NORMAL_3", NORMAL));
        queue.offer(message("NORMAL_4", NORMAL));
        queue.offer(message("NORMAL_5", NORMAL));
        queue.offer(message("NORMAL_6", NORMAL));
        queue.offer(message("URGENT_1", URGENT));
        queue.offer(message("URGENT_2", URGENT));
        queue.offer(message("URGENT_3", URGENT));
        queue.offer(message("URGENT_4", URGENT));
        queue.offer(message("URGENT_5", URGENT));
        queue.offer(message("URGENT_6", URGENT));
        queue.offer(message("URGENT_7", URGENT));
        queue.offer(message("URGENT_8", URGENT));
        queue.offer(message("URGENT_9", URGENT));
        queue.offer(message("URGENT_10", URGENT));
        queue.offer(message("URGENT_11", URGENT));
        queue.offer(message("URGENT_12", URGENT));
        queue.offer(message("URGENT_13", URGENT));
        queue.offer(message("URGENT_14", URGENT));

        assertEquals(20, queue.size());

        // primeiro ciclo de prioridade 10 = 3 NORMAL + 7 URGENT
        assertEquals("NORMAL_1", queue.poll().getContent());
        assertEquals("URGENT_1", queue.poll().getContent());
        assertEquals("NORMAL_2", queue.poll().getContent());
        assertEquals("URGENT_2", queue.poll().getContent());
        assertEquals("NORMAL_3", queue.poll().getContent());
        assertEquals("URGENT_3", queue.poll().getContent());
        assertEquals("URGENT_4", queue.poll().getContent());
        assertEquals("URGENT_5", queue.poll().getContent());
        assertEquals("URGENT_6", queue.poll().getContent());
        assertEquals("URGENT_7", queue.poll().getContent());

        // segundo ciclo de prioridade 10 = 3 NORMAL + 7 URGENT
        assertEquals("NORMAL_4", queue.poll().getContent());
        assertEquals("URGENT_8", queue.poll().getContent());
        assertEquals("NORMAL_5", queue.poll().getContent());
        assertEquals("URGENT_9", queue.poll().getContent());
        assertEquals("NORMAL_6", queue.poll().getContent());
        assertEquals("URGENT_10", queue.poll().getContent());
        assertEquals("URGENT_11", queue.poll().getContent());
        assertEquals("URGENT_12", queue.poll().getContent());
        assertEquals("URGENT_13", queue.poll().getContent());
        assertEquals("URGENT_14", queue.poll().getContent());

        assertTrue(queue.isEmpty());
    }

    @Test
    void sholdReturnPrioritizedBasedOnDemandMessages() {
        var queue = new MessageQueuePrioritizer();
        queue.offer(message("NORMAL_1", NORMAL));
        queue.offer(message("NORMAL_2", NORMAL));
        queue.offer(message("NORMAL_3", NORMAL));
        queue.offer(message("URGENT_1", URGENT));
        queue.offer(message("URGENT_2", URGENT));
        queue.offer(message("URGENT_3", URGENT));

        assertEquals(6, queue.size());

        assertEquals("NORMAL_1", queue.poll().getContent());
        assertEquals("URGENT_1", queue.poll().getContent());
        assertEquals("NORMAL_2", queue.poll().getContent());
        assertEquals("URGENT_2", queue.poll().getContent());
        assertEquals("NORMAL_3", queue.poll().getContent());
        assertEquals("URGENT_3", queue.poll().getContent());

        assertTrue(queue.isEmpty());

        queue.offer(message("NORMAL_4", NORMAL));
        queue.offer(message("NORMAL_5", NORMAL));
        queue.offer(message("NORMAL_6", NORMAL));
        queue.offer(message("URGENT_4", URGENT));
        queue.offer(message("URGENT_5", URGENT));
        queue.offer(message("URGENT_6", URGENT));

        assertEquals("URGENT_4", queue.poll().getContent());
        assertEquals("URGENT_5", queue.poll().getContent());
        assertEquals("URGENT_6", queue.poll().getContent());

        assertEquals(3, queue.size());

        queue.offer(message("URGENT_7", URGENT));
        queue.offer(message("URGENT_8", URGENT));

        assertEquals("URGENT_7", queue.poll().getContent());
        assertEquals("NORMAL_4", queue.poll().getContent());
        assertEquals("URGENT_8", queue.poll().getContent());
        assertEquals("NORMAL_5", queue.poll().getContent());
        assertEquals("NORMAL_6", queue.poll().getContent());

        assertTrue(queue.isEmpty());
    }

    Message message(String content, MessagePriority priority) {
        return new Message(
                "123",
                "client123",
                "user789",
                content,
                Instant.now(),
                priority,
                MessageStatus.QUEUED,
                priority.cost());
    }
}