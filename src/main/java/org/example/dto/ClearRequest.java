package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 清空会话请求 DTO
 */
@Setter
@Getter
@Schema(description = "清空会话历史请求")
public class ClearRequest {

    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "session-123")
    @JsonProperty(value = "Id")
    @JsonAlias({"id", "ID"})
    private String Id;
}