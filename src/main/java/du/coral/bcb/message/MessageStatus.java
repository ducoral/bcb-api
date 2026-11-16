package du.coral.bcb.message;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageStatus {
    QUEUED,
    PROCESSING,
    SENT,
    DELIVERED,
    READ,
    FAILED;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}