package du.coral.bcb.billing;

import du.coral.bcb.client.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class MonthlyLimitResetJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyLimitResetJob.class);

    private final ClientRepository repository;
    private final Clock clock;

    public MonthlyLimitResetJob(ClientRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Todo dia 1º, à meia-noite no fuso de cobrança. */
    @Scheduled(cron = "0 0 0 1 * *", zone = BillingConfig.BILLING_ZONE)
    @Transactional
    public void run() {
        int renewed = repository.resetMonthlyLimits(LocalDate.now(clock).withDayOfMonth(1));
        log.info("Limite mensal restaurado para {} cliente(s) pós-pago(s)", renewed);
    }
}