package du.coral.bcb.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "JWT para o header Authorization: Bearer")
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Validade em segundos", example = "3600")
        long expiresIn
) {}