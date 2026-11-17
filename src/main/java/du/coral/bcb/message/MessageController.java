package du.coral.bcb.message;

import du.coral.bcb.auth.JwtAuthFilter;
import du.coral.bcb.message.web.MessagePageResponse;
import du.coral.bcb.message.web.MessageRequest;
import du.coral.bcb.message.web.MessageResponse;
import du.coral.bcb.message.web.MessageStatusResponse;
import du.coral.bcb.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

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

    @Operation(summary = "Lista as mensagens do cliente autenticado",
            description = "Mais recentes primeiro. Todos os filtros são opcionais e combináveis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de mensagens"),
            @ApiResponse(responseCode = "400", description = "Filtro ou paginação inválidos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content)
    })
    @GetMapping
    public MessagePageResponse list(
            @RequestAttribute(JwtAuthFilter.CLIENT_ID_ATTRIBUTE)
            String clientId,

            @Parameter(description = "queued, processing, sent, delivered, read ou failed")
            @RequestParam(required = false)
            MessageStatus status,

            @Parameter(description = "normal ou urgent")
            @RequestParam(required = false)
            MessagePriority priority,

            @RequestParam(required = false)
            String recipientId,

            @Parameter(description = "Início do período (inclusivo), ISO-8601. Ex.: 2026-11-15T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @Parameter(description = "Fim do período (inclusivo), ISO-8601")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(defaultValue = "0") @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size) {

        if (from != null && to != null && from.isAfter(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from deve ser anterior ou igual a to");

        return MessagePageResponse.from(
                service.search(clientId, status, priority, recipientId, from, to, page, size));
    }

    @Operation(summary = "Obtém os detalhes de uma mensagem")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensagem encontrada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mensagem não encontrada (ou de outro cliente)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public MessageResponse get(
            @RequestAttribute(JwtAuthFilter.CLIENT_ID_ATTRIBUTE)
            String clientId,

            @PathVariable
            String id) {

        return MessageResponse.from(service.findOwned(id, clientId));
    }

    @Operation(summary = "Verifica o status de uma mensagem")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atual"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mensagem não encontrada (ou de outro cliente)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}/status")
    public MessageStatusResponse status(
            @RequestAttribute(JwtAuthFilter.CLIENT_ID_ATTRIBUTE)
            String clientId,

            @PathVariable
            String id) {

        return MessageStatusResponse.from(service.findOwned(id, clientId));
    }
}