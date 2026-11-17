package du.coral.bcb.message.web;

import du.coral.bcb.message.Message;
import du.coral.bcb.message.MessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Status atual de uma mensagem")
public record MessageStatusResponse(String id, MessageStatus status) {

    public static MessageStatusResponse from(Message m) {
        return new MessageStatusResponse(m.getId(), m.getStatus());
    }
}