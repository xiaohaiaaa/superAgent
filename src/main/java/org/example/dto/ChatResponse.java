package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一聊天响应 DTO
 */
@Setter
@Getter
@Schema(description = "聊天响应")
public class ChatResponse {

    @Schema(description = "是否成功", example = "true")
    private boolean success;

    @Schema(description = "AI 回复内容", example = "你好，有什么可以帮助你的吗？")
    private String answer;

    @Schema(description = "错误信息", example = "服务暂不可用")
    private String errorMessage;

    public static ChatResponse success(String answer) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        return response;
    }

    public static ChatResponse error(String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}