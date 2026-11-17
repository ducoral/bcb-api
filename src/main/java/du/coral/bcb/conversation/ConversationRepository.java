package du.coral.bcb.conversation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    Optional<Conversation> findByClientIdAndRecipientId(String clientId, String recipientId);

    Optional<Conversation> findByIdAndClientId(String id, String clientId);

    Page<Conversation> findByClientId(String clientId, Pageable pageable);
}