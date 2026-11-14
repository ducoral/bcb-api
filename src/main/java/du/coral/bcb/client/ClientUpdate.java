package du.coral.bcb.client;

import java.math.BigDecimal;

public record ClientUpdate(
        String name,
        String password,
        String documentId,
        DocumentType documentType,
        PlanType planType,
        BigDecimal balance,
        BigDecimal limit,
        boolean active
) {
}