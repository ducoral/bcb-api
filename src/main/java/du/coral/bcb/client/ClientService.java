package du.coral.bcb.client;

import du.coral.bcb.shared.error.ConflictException;
import du.coral.bcb.shared.error.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClientService {

    private final ClientRepository repository;

    private final PasswordEncoder passwordEncoder;

    public ClientService(ClientRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Client create(Client client) {
        if (repository.existsById(client.getId())) {
            throw new ConflictException("Já existe cliente com id " + client.getId());
        }
        if (repository.existsByDocumentId(client.getDocumentId())) {
            throw new ConflictException("Já existe cliente com este documento");
        }
        client.setPassword(passwordEncoder.encode(client.getPassword()));
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
        var encodedPassword = data.password() == null
                ? null
                : passwordEncoder.encode(data.password());
        client.update(data, encodedPassword);
        return repository.save(client);
    }
}