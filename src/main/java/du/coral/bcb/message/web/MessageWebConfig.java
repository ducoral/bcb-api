package du.coral.bcb.message.web;

import du.coral.bcb.message.MessagePriority;
import du.coral.bcb.message.MessageStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration
public class MessageWebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(
                String.class,
                MessageStatus.class,
                s -> s.isBlank()
                        ? null
                        : MessageStatus.valueOf(s.trim().toUpperCase(Locale.ROOT)));

        registry.addConverter(
                String.class,
                MessagePriority.class,
                s -> s.isBlank()
                        ? null
                        : MessagePriority.valueOf(s.trim().toUpperCase(Locale.ROOT)));
    }
}