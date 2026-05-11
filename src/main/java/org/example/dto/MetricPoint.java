package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 指标数据点
 */
@Data
@Schema(description = "指标数据点")
public class MetricPoint {

    @Schema(description = "时间戳（Unix epoch 秒）", example = "1714713600")
    private long timestamp;

    @Schema(description = "指标值", example = "92.5")
    private double value;

    public MetricPoint() {}

    public MetricPoint(long timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }
}