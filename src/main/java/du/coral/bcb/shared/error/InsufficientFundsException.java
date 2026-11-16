package du.coral.bcb.shared.error;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientFundsException(String message, BigDecimal required, BigDecimal available) {
        super(message);
        this.required = required;
        this.available = available;
    }

    public BigDecimal getRequired() {
        return required;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}