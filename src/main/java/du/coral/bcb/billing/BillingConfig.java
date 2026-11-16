package du.coral.bcb.billing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class BillingConfig {

    public static final String BILLING_ZONE = "America/Sao_Paulo";

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of(BILLING_ZONE));
    }
}