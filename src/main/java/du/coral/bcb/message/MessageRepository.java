package du.coral.bcb.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByStatusOrderByTimestampAsc(MessageStatus status);
}