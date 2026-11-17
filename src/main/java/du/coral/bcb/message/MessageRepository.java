package du.coral.bcb.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, String>, JpaSpecificationExecutor<Message> {

    List<Message> findByStatusOrderByTimestampAsc(MessageStatus status);

    Optional<Message> findByIdAndSenderId(String id, String senderId);
}