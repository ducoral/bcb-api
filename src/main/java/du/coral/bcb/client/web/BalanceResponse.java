package du.coral.bcb.client.web;

import du.coral.bcb.client.Client;
import du.coral.bcb.client.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Saldo e limite do cliente")
public record BalanceResponse(
        String clientId,
        PlanType planType,
        BigDecimal balance,
        BigDecimal limit
) {
    public static BalanceResponse from(Client c) {
        return new BalanceResponse(
                c.getId(),
                c.getPlanType(),
                c.getBalance(),
                c.getLimit());
    }
}