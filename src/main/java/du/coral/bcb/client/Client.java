package du.coral.bcb.client;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "client", uniqueConstraints = @UniqueConstraint(columnNames = "document_id"))
public class Client {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Column(name = "document_id", nullable = false, length = 14)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 4)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 10)
    private PlanType planType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "credit_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal limit = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    public Client(
            String id,
            String name,
            String password,
            String documentId,
            DocumentType documentType,
            PlanType planType,
            BigDecimal balance,
            BigDecimal limit,
            boolean active) {
        this.id = id;
        this.name = name;
        this.password = password;
        this.documentId = documentId;
        this.documentType = documentType;
        this.planType = planType;
        this.balance = balance;
        this.limit = limit;
        this.active = active;
    }

    protected Client() {
    }

    public void update(ClientUpdate data) {
        this.name = data.name();
        this.documentId = data.documentId();
        this.documentType = data.documentType();
        this.planType = data.planType();
        this.balance = data.balance();
        this.limit = data.limit();
        this.active = data.active();
        if (data.password() != null) {
            this.password = data.password();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getLimit() {
        return limit;
    }

    public void setLimit(BigDecimal limit) {
        this.limit = limit;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}