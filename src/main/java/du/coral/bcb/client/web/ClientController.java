package du.coral.bcb.client.web;

import du.coral.bcb.client.ClientService;
import du.coral.bcb.shared.config.OpenApiConfig;
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
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/clients")
@Tag(name = "Clientes (admin)", description = "Gerenciamento administrativo de clientes")
@SecurityRequirement(name = OpenApiConfig.ADMIN_AUTH)
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @Operation(summary = "Cadastra um novo cliente",
            description = "Persiste um cliente pré-pago ou pós-pago. O documentId deve conter apenas dígitos.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201", description = "Cliente criado",
                    headers = @Header(
                            name = "Location", description = "URI do cliente criado",
                            schema = @Schema(type = "string", example = "/clients/client123"))),
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
    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody ClientCreateRequest request) {
        var client = service.create(request.toEntity());
        return ResponseEntity
                .created(URI.create("/clients/" + client.getId()))
                .body(ClientResponse.from(client));
    }

    @Operation(summary = "Lista clientes")
    @GetMapping
    public List<ClientResponse> list() {
        return service.findAll().stream().map(ClientResponse::from).toList();
    }

    @Operation(summary = "Obtém detalhes de um cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable String id) {
        return ClientResponse.from(service.findById(id));
    }

    @Operation(summary = "Atualiza um cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Documento já pertence a outro cliente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable String id, @Valid @RequestBody ClientUpdateRequest request) {
        return ClientResponse.from(service.update(id, request.toCommand()));
    }

    @Operation(summary = "Consulta saldo e limite do cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo e limite"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable String id) {
        return BalanceResponse.from(service.findById(id));
    }
}
