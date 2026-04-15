package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AIOps 请求 DTO
 */
@Data
@Schema(description = "AIOps请求")
public class AIOpsRequest {

    @Schema(description = "用户请求描述", example = "帮我分析最近的系统告警")
    private String userRequest;
}