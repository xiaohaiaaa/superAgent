package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 会话消息 DTO
 */
@Setter
@Getter
@NoArgsConstructor
@Schema(description = "会话消息")
public class SessionMessage {

    @Schema(description = "消息角色 (user/assistant)")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "时间戳")
    private long timestamp;

    public SessionMessage(String role, String content, long timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }
}