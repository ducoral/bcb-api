package du.coral.bcb.client;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, String> {
    boolean existsByDocumentId(String documentId);

    boolean existsByDocumentIdAndIdNot(String documentId, String id);
}