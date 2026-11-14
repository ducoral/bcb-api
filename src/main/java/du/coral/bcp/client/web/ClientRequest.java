package du.coral.bcp.client.web;

import du.coral.bcp.client.Client;
import du.coral.bcp.client.DocumentType;
import du.coral.bcp.client.PlanType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ClientRequest(
        @NotBlank
        @Size(max = 255)
        String id,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 255)
        String password,

        @NotBlank
        @Pattern(regexp = "\\d{11}|\\d{14}", message = "deve conter apenas dígitos (11 para CPF, 14 para CNPJ)")
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