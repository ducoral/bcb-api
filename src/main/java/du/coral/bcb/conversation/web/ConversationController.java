package du.coral.bcb.conversation.web;

import du.coral.bcb.auth.JwtAuthFilter;
import du.coral.bcb.conversation.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conversations")
@Tag(name = "Conversas")
@SecurityRequirement(name = "clientJwt")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @Operation(summary = "Lista as conversas do cliente autenticado (mais recentes primeiro)")
    @GetMapping
    public PageResponse<ConversationResponse> list(
            @RequestAttribute(JwtAuthFilter.CLIENT_ID_ATTRIBUTE)
            String clientId,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size) {
        return PageResponse.from(service.list(clientId, page, size), ConversationResponse::from);
    }

    @Operation(summary = "Obtém os detalhes de uma conversa")
    @ApiResponse(responseCode = "404", description = "Conversa não encontrada (ou de outro cliente)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public ConversationResponse get(
            @RequestAttribute(JwtAuthFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable String id) {
        return ConversationResponse.from(service.get(id, clientId));
    }

    @Operation(summary = "Lista as mensagens de uma conversa (mais novas primeiro)")
    @ApiResponse(responseCode = "404", description = "Conversa não encontrada (ou de outro cliente)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}/messages")
    public PageResponse<ConversationMessageResponse> messages(
            @RequestAttribute(JwtAuthFilter.CLIENT_ID_ATTRIBUTE)
            String clientId,

            @PathVariable
            String id,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size) {
        return PageResponse.from(service.messages(id, clientId, page, size),
                ConversationMessageResponse::from);
    }
}