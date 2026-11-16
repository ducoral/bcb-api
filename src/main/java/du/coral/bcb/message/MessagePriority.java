package du.coral.bcb.message;

import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;

public enum MessagePriority {
    NORMAL(new BigDecimal("0.25")),
    URGENT(new BigDecimal("0.50"));

    private final BigDecimal cost;

    MessagePriority(BigDecimal cost) {
        this.cost = cost;
    }

    public BigDecimal cost() {
        return cost;
    }

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}