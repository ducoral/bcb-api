package du.coral.bcb.message.web;

import du.coral.bcb.message.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Página de mensagens")
public record MessagePageResponse(
        List<MessageResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static MessagePageResponse from(Page<Message> result) {

        return new MessagePageResponse(
                result.getContent().stream().map(MessageResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}