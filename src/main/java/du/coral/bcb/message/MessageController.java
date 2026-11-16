package du.coral.bcb.message;

import du.coral.bcb.auth.JwtAuthFilter;
import du.coral.bcb.message.web.MessageRequest;
import du.coral.bcb.message.web.MessageResponse;
import du.coral.bcb.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/messages")
@Tag(name = "Mensagens")
@SecurityRequirement(name = OpenApiConfig.CLIENT_JWT)
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @Operation(summary = "Envia uma mensagem",
            description = "Persiste a mensagem com status queued e a adiciona à fila de processamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Mensagem persistida e enfileirada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content),
            @ApiResponse(responseCode = "402", description = "Saldo (pré-pago) ou limite mensal (pós-pago) insuficiente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Cliente inativo",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping
    public ResponseEntity<MessageResponse> create(
            @RequestAttribute(JwtAuthFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @Valid @RequestBody MessageRequest request) {
        var saved = service
                .send(clientId, request.recipientId(), request.content(), request.priority());
        return ResponseEntity
                .accepted()
                .body(MessageResponse.from(saved));
    }
}