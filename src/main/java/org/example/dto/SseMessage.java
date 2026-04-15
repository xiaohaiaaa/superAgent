package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一 SSE 流式消息 DTO
 */
@Setter
@Getter
@Schema(description = "SSE流式消息")
public class SseMessage {

    @Schema(description = "消息类型：content-内容块, error-错误, done-完成", example = "content")
    private String type;

    @Schema(description = "消息数据", example = "你好")
    private String data;

    public static SseMessage content(String data) {
        SseMessage message = new SseMessage();
        message.setType("content");
        message.setData(data);
        return message;
    }

    public static SseMessage error(String errorMessage) {
        SseMessage message = new SseMessage();
        message.setType("error");
        message.setData(errorMessage);
        return message;
    }

    public static SseMessage done() {
        SseMessage message = new SseMessage();
        message.setType("done");
        message.setData(null);
        return message;
    }
}