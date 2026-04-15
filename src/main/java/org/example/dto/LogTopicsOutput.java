package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 日志主题列表输出 DTO
 */
@Setter
@Getter
@Schema(description = "日志主题列表输出")
public class LogTopicsOutput {

    @JsonProperty("success")
    @Schema(description = "是否成功")
    private boolean success;

    @JsonProperty("topics")
    @Schema(description = "主题列表")
    private List<LogTopicInfo> topics;

    @JsonProperty("available_regions")
    @Schema(description = "可用地域列表")
    private List<String> availableRegions;

    @JsonProperty("default_region")
    @Schema(description = "默认地域")
    private String defaultRegion;

    @JsonProperty("message")
    @Schema(description = "消息")
    private String message;
}