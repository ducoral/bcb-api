package du.coral.bcp.client;

import du.coral.bcp.shared.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    private final ClientRepository repository;

    public ClientService(ClientRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Client create(Client client) {
        if (repository.existsById(client.getId())) {
            throw new ConflictException("Já existe cliente com id " + client.getId());
        }
        if (repository.existsByDocumentId(client.getDocumentId())) {
            throw new ConflictException("Já existe cliente com este documento");
        }
        return repository.save(client);
    }
}