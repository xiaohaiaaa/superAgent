package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Prometheus 告警查询结果 DTO
 */
@Setter
@Getter
@Schema(description = "Prometheus 告警查询结果")
public class PrometheusAlertsResult {

    @Schema(description = "查询状态")
    private String status;

    @Schema(description = "告警数据")
    private AlertsData data;

    @Schema(description = "错误信息")
    private String error;

    @Schema(description = "错误类型")
    private String errorType;

    @Setter
    @Getter
    @Schema(description = "告警数据容器")
    public static class AlertsData {
        private List<PrometheusAlert> alerts = new ArrayList<>();
    }
}