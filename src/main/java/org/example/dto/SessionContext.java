package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent 会话上下文 DTO
 */
@Setter
@Getter
@Schema(description = "Agent 会话上下文")
public class SessionContext {

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "创建时间")
    private long createTime;

    @Schema(description = "最后访问时间")
    private long lastAccessTime;
}