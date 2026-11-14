package du.coral.bcp.client.web;

import du.coral.bcp.client.Client;
import du.coral.bcp.client.DocumentType;
import du.coral.bcp.client.PlanType;

import java.math.BigDecimal;

public record ClientResponse(
        String id,
        String name,
        String documentId,
        DocumentType documentType,
        PlanType planType,
        BigDecimal balance,
        BigDecimal limit,
        boolean active
) {
    public static ClientResponse from(Client c) {
        return new ClientResponse(
                c.getId(),
                c.getName(),
                c.getDocumentId(),
                c.getDocumentType(),
                c.getPlanType(),
                c.getBalance(),
                c.getLimit(),
                c.isActive());
    }
}