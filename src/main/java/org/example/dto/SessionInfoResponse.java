package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话信息响应 DTO
 */
@Setter
@Getter
@Schema(description = "会话信息响应")
public class SessionInfoResponse {

    @Schema(description = "会话ID", example = "session-123")
    private String sessionId;

    @Schema(description = "消息对数", example = "5")
    private int messagePairCount;

    @Schema(description = "创建时间戳", example = "1712345678900")
    private long createTime;
}