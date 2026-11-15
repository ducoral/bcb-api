package du.coral.bcb.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String ADMIN_AUTH = "adminAuth";
    public static final String ADMIN_AUTH_DESC =
            "Informe apenas o token (ex.: admin-token). O prefixo `Bearer` é adicionado automaticamente.";

    public static final String CLIENT_JWT = "clientJwt";
    public static final String CLIENT_JWT_DESC = "JWT obtido em POST /auth/login";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BCP API")
                        .description("API administrativa de clientes")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(ADMIN_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description(ADMIN_AUTH_DESC))
                        .addSecuritySchemes(CLIENT_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(CLIENT_JWT_DESC))
                );
    }
}