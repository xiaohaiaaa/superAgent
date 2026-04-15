package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 聊天请求 DTO
 */
@Setter
@Getter
@Schema(description = "聊天请求")
public class ChatRequest {

    @Schema(description = "会话ID，不传则自动生成", example = "session-123")
    @JsonProperty(value = "Id")
    @JsonAlias({"id", "ID"})
    private String Id;

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，请介绍一下自己")
    @JsonProperty(value = "Question")
    @JsonAlias({"question", "QUESTION"})
    private String Question;
}