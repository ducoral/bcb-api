package du.coral.bcp.admin;

import du.coral.bcp.client.ClientService;
import du.coral.bcp.client.web.ClientRequest;
import du.coral.bcp.client.web.ClientResponse;
import du.coral.bcp.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/admin")
@Tag(name = "Clientes (admin)", description = "Gerenciamento administrativo de clientes")
@SecurityRequirement(name = OpenApiConfig.ADMIN_AUTH)
public class AdminController {

    private final ClientService clientService;

    public AdminController(ClientService clientService) {
        this.clientService = clientService;
    }

    @Operation(summary = "Cadastra um novo cliente",
            description = "Persiste um cliente pré-pago ou pós-pago. O documentId deve conter apenas dígitos.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201", description = "Cliente criado",
                    headers = @Header(
                            name = "Location", description = "URI do cliente criado",
                            schema = @Schema(type = "string", example = "/admin/client/client123"))),
            @ApiResponse(responseCode = "400",
                    description = "Dados inválidos",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "409", description = "Id ou documento já cadastrado",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/client")
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody ClientRequest request) {
        var client = clientService.create(request.toEntity());
        return ResponseEntity
                .created(URI.create("/admin/client/" + client.getId()))
                .body(ClientResponse.from(client));
    }
}
