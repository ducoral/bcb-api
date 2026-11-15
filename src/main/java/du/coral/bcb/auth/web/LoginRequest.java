package du.coral.bcb.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "client123")
        @NotBlank String id,

        @Schema(example = "senha123")
        @NotBlank String password
) {}