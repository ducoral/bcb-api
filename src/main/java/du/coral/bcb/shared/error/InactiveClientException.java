package du.coral.bcb.shared.error;

public class InactiveClientException extends RuntimeException {
    public InactiveClientException() {
        super("Cliente inativo");
    }
}