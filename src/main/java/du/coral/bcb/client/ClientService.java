package du.coral.bcb.client;

import du.coral.bcb.shared.error.ConflictException;
import du.coral.bcb.shared.error.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<Client> findAll() {
        return repository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Client findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + id));
    }

    @Transactional
    public Client update(String id, ClientUpdate data) {
        var client = findById(id);
        if (repository.existsByDocumentIdAndIdNot(data.documentId(), id))
            throw new ConflictException("Já existe cliente com este documento");
        client.update(data);
        return repository.save(client);
    }
}