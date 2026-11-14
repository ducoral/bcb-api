package du.coral.bcp.admin;

import du.coral.bcp.client.ClientService;
import du.coral.bcp.client.web.ClientRequest;
import du.coral.bcp.client.web.ClientResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ClientService clientService;

    public AdminController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping("/client")
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody ClientRequest request) {
        var client = clientService.create(request.toEntity());
        return ResponseEntity
                .created(URI.create("/admin/client/" + client.getId()))
                .body(ClientResponse.from(client));
    }
}
