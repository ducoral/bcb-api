package du.coral.bcb.client.web;

import du.coral.bcb.client.ClientUpdate;
import du.coral.bcb.client.DocumentType;
import du.coral.bcb.client.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Dados para atualização de cliente")
public record ClientUpdateRequest(
        @Schema(example = "Empresa ABC Ltda")
        @NotBlank
        @Size(max = 255)
        String name,

        @Schema(description = "Nova senha. Se omitida, a senha atual é mantida",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 72)
        String password,

        @Schema(description = "CPF (11 dígitos) ou CNPJ (14 dígitos), somente números", example = "12345678000199")
        @NotBlank
        @Pattern(regexp = "\\d{11}|\\d{14}")
        String documentId,

        @NotNull
        DocumentType documentType,

        @NotNull
        PlanType planType,

        @PositiveOrZero
        BigDecimal balance,

        @PositiveOrZero
        BigDecimal limit,

        Boolean active
) {

    @Schema(hidden = true)
    @AssertTrue(message = "documentId não corresponde ao documentType informado")
    public boolean isDocumentConsistent() {
        if (documentId == null || documentType == null) return true;
        return switch (documentType) {
            case CPF -> documentId.length() == 11;
            case CNPJ -> documentId.length() == 14;
        };
    }

    public ClientUpdate toCommand() {
        return new ClientUpdate(
                name,
                password,
                documentId,
                documentType,
                planType,
                balance != null ? balance : BigDecimal.ZERO,
                limit != null ? limit : BigDecimal.ZERO,
                active == null || active);
    }
}