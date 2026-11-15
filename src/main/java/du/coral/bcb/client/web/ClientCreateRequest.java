package du.coral.bcb.client.web;

import du.coral.bcb.client.Client;
import du.coral.bcb.client.DocumentType;
import du.coral.bcb.client.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Dados para cadastro de cliente")
public record ClientCreateRequest(
        @Schema(description = "Identificador do cliente", example = "client123")
        @NotBlank
        @Size(max = 64)
        String id,

        @Schema(description = "Razão social ou nome", example = "Empresa ABC Ltda")
        @NotBlank
        @Size(max = 255)
        String name,

        @Schema(description = "Senha de acesso", example = "senha123", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(max = 72)
        String password,

        @Schema(description = "CPF (11 dígitos) ou CNPJ (14 dígitos), somente números", example = "12345678000199")
        @NotBlank @Pattern(regexp = "\\d{11}|\\d{14}")
        String documentId,

        @Schema(description = "Tipo do documento", example = "CNPJ")
        @NotNull
        DocumentType documentType,

        @Schema(description = "Tipo de plano", example = "PREPAID")
        @NotNull
        PlanType planType,

        @Schema(description = "Saldo (pré-pago)", example = "100.00", defaultValue = "0")
        @PositiveOrZero
        BigDecimal balance,

        @Schema(description = "Limite de crédito (pós-pago)", example = "0", defaultValue = "0")
        @PositiveOrZero
        BigDecimal limit,

        @Schema(description = "Status do cliente", example = "true", defaultValue = "true")
        Boolean active
) {

    @Schema(hidden = true)
    @AssertTrue(message = "documentId não corresponde ao documentType informado")
    public boolean isDocumentConsistent() {
        if (documentId == null || documentType == null)
            return true;
        return switch (documentType) {
            case CPF -> documentId.length() == 11;
            case CNPJ -> documentId.length() == 14;
        };
    }

    public Client toEntity() {
        return new Client(
                id,
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