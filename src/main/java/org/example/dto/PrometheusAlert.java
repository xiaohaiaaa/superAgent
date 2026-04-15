package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Prometheus 告警信息 DTO
 */
@Setter
@Getter
@Schema(description = "Prometheus 告警信息")
public class PrometheusAlert {

    @Schema(description = "告警标签 Map")
    private java.util.Map<String, String> labels;

    @Schema(description = "告警注释 Map")
    private java.util.Map<String, String> annotations;

    @Schema(description = "告警状态")
    private String state;

    @Schema(description = "激活时间")
    private String activeAt;

    @Schema(description = "告警值")
    private String value;
}