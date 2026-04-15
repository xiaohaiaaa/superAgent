package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 日志主题信息 DTO
 */
@Setter
@Getter
@Schema(description = "日志主题信息")
public class LogTopicInfo {

    @JsonProperty("topic_name")
    @Schema(description = "主题名称")
    private String topicName;

    @JsonProperty("description")
    @Schema(description = "主题描述")
    private String description;

    @JsonProperty("example_queries")
    @Schema(description = "示例查询")
    private List<String> exampleQueries;

    @JsonProperty("related_alerts")
    @Schema(description = "关联告警")
    private List<String> relatedAlerts;
}