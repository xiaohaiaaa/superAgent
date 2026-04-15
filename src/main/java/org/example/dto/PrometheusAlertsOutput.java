package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Prometheus 告警输出 DTO
 */
@Setter
@Getter
@Schema(description = "Prometheus 告警输出")
public class PrometheusAlertsOutput {

    @JsonProperty("success")
    @Schema(description = "是否成功")
    private boolean success;

    @JsonProperty("alerts")
    @Schema(description = "告警列表")
    private List<SimplifiedAlert> alerts;

    @JsonProperty("message")
    @Schema(description = "消息")
    private String message;

    @JsonProperty("error")
    @Schema(description = "错误信息")
    private String error;
}