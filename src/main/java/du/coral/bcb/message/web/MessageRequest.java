package du.coral.bcb.message.web;

import du.coral.bcb.message.MessagePriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para envio de mensagem. O remetente é o cliente autenticado pelo JWT.")
public record MessageRequest(
        @Schema(example = "user789")
        @NotBlank @Size(max = 64) String recipientId,

        @Schema(example = "Olá, como vai?")
        @NotBlank @Size(max = 4000) String content,

        @Schema(description = "Prioridade (padrão: normal)", example = "normal")
        MessagePriority priority
) {}