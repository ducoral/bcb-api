package du.coral.bcb.message;

import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;

public enum MessagePriority {
    NORMAL(3, new BigDecimal("0.25")),
    URGENT(7, new BigDecimal("0.50"));

    private final int weight;
    private final BigDecimal cost;

    MessagePriority(int weight, BigDecimal cost) {
        this.weight = weight;
        this.cost = cost;
    }

    public int weight() {
        return weight;
    }

    public BigDecimal cost() {
        return cost;
    }

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}