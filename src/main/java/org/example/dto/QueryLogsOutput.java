package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 日志查询输出 DTO
 */
@Setter
@Getter
@Schema(description = "日志查询输出")
public class QueryLogsOutput {

    @JsonProperty("success")
    @Schema(description = "是否成功")
    private boolean success;

    @JsonProperty("region")
    @Schema(description = "地域")
    private String region;

    @JsonProperty("log_topic")
    @Schema(description = "日志主题")
    private String logTopic;

    @JsonProperty("query")
    @Schema(description = "查询条件")
    private String query;

    @JsonProperty("logs")
    @Schema(description = "日志列表")
    private List<LogEntry> logs;

    @JsonProperty("total")
    @Schema(description = "总数")
    private int total;

    @JsonProperty("message")
    @Schema(description = "消息")
    private String message;
}