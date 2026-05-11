package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标时间序列结果
 */
@Data
@Schema(description = "指标时间序列结果")
public class MetricTimeSeries {

    @Schema(description = "指标名称", example = "cpu_usage")
    private String metricName;

    @Schema(description = "服务名称", example = "payment-service")
    private String service;

    @Schema(description = "实例名称", example = "pod-payment-service-7d8f9c6b5-x2k4m")
    private String instance;

    @Schema(description = "数据点列表")
    private List<MetricPoint> points;

    @Schema(description = "查询时间范围（秒）", example = "3600")
    private long rangeSeconds;

    @Schema(description = "查询步长（秒）", example = "15")
    private long stepSeconds;

    @Schema(description = "最小值")
    private double minValue;

    @Schema(description = "最大值")
    private double maxValue;

    @Schema(description = "平均值")
    private double avgValue;

    @Schema(description = "当前值")
    private double currentValue;

    @Schema(description = "成功标志")
    private boolean success;

    @Schema(description = "消息")
    private String message;

    public MetricTimeSeries() {
        this.points = new ArrayList<>();
        this.success = true;
    }

    /**
     * 计算统计值
     */
    public void calculateStats() {
        if (points == null || points.isEmpty()) {
            return;
        }

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (MetricPoint point : points) {
            double v = point.getValue();
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        this.minValue = min;
        this.maxValue = max;
        this.avgValue = sum / points.size();

        // 当前值为最后一个点
        if (!points.isEmpty()) {
            this.currentValue = points.get(points.size() - 1).getValue();
        }
    }
}