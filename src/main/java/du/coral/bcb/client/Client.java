package du.coral.bcb.client;

import du.coral.bcb.shared.error.InsufficientFundsException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Column(name = "monthly_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyLimit = BigDecimal.ZERO;

    @Column(name = "limit_period")
    private LocalDate limitPeriod;   // 1º dia do mês a que o limit atual se refere; null = ainda sem consumo

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

    public void update(ClientUpdate data, String encodedPassword) {
        this.name = data.name();
        this.documentId = data.documentId();
        this.documentType = data.documentType();
        this.planType = data.planType();
        this.balance = data.balance();
        this.limit = data.limit();
        this.monthlyLimit = limit;
        this.active = data.active();
        if (encodedPassword != null) {
            this.password = encodedPassword;
        }
    }

    /** Pré-pago: debita o balance. Pós-pago: renova o limite se virou o mês e debita o limit. */
    public void charge(BigDecimal cost, LocalDate currentPeriod) {
        switch (planType) {
            case PREPAID -> {
                if (balance.compareTo(cost) < 0) {
                    throw new InsufficientFundsException("Saldo insuficiente", cost, balance);
                }
                balance = balance.subtract(cost);
            }
            case POSTPAID -> {
                renewLimitIfNewPeriod(currentPeriod);
                if (limit.compareTo(cost) < 0) {
                    throw new InsufficientFundsException("Limite mensal insuficiente", cost, limit);
                }
                limit = limit.subtract(cost);
            }
        }
    }

    /** Restaura o limite mensal quando o mês mudou. Na primeira cobrança só registra o período. */
    public void renewLimitIfNewPeriod(LocalDate currentPeriod) {
        if (limitPeriod == null) {
            limitPeriod = currentPeriod;
        } else if (limitPeriod.isBefore(currentPeriod)) {
            limit = monthlyLimit;
            limitPeriod = currentPeriod;
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

    public void setPassword(String encodedPassword) {
        this.password = encodedPassword;
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

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public LocalDate getLimitPeriod() {
        return limitPeriod;
    }

    public void setLimitPeriod(LocalDate limitPeriod) {
        this.limitPeriod = limitPeriod;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}