package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 日志条目 DTO
 */
@Setter
@Getter
@Schema(description = "日志条目")
public class LogEntry {

    @JsonProperty("timestamp")
    @Schema(description = "时间戳")
    private String timestamp;

    @JsonProperty("level")
    @Schema(description = "日志级别")
    private String level;

    @JsonProperty("service")
    @Schema(description = "服务名称")
    private String service;

    @JsonProperty("instance")
    @Schema(description = "实例名称")
    private String instance;

    @JsonProperty("message")
    @Schema(description = "日志消息")
    private String message;

    @JsonProperty("metrics")
    @Schema(description = "指标数据 Map")
    private Map<String, String> metrics;
}