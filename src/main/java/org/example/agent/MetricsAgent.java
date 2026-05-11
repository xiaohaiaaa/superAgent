package org.example.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.QueryPrometheusMetricsTools;
import org.example.dto.AiOpsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MetricsAgent - 指标查询 Agent
 * 专门负责从 Prometheus 查询指标数据（时间序列）
 * 数据维度：服务级别（serviceId）
 *
 * 与 AlertAgent 的区别：
 * - AlertAgent 查询的是"告警事件"（是否有故障触发）
 * - MetricsAgent 查询的是"指标趋势"（数值变化历史）
 */
@Component
public class MetricsAgent {

    private static final Logger logger = LoggerFactory.getLogger(MetricsAgent.class);

    public static final String AGENT_NAME = "metrics_agent";
    public static final String OUTPUT_KEY = "metrics_result";

    // 默认查询时间范围（分钟）
    private static final int DEFAULT_RANGE_MINUTES = 60;

    @Autowired
    private QueryPrometheusMetricsTools metricsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    /**
     * 执行指标查询
     * 根据告警类型决定查询哪些指标
     */
    public String execute(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks) {
        logger.info("MetricsAgent 开始执行，tenantId: {}, serviceId: {}, alertName: {}",
                context.getTenantId(), context.getServiceId(), context.getAlertName());

        try {
            String service = context.getServiceId();
            String alertType = context.getSharedData("alert_type", "UNKNOWN");
            String queryHint = context.getSharedData("query_hint", "");

            // 根据告警类型构建查询计划
            StringBuilder results = new StringBuilder();
            results.append("=== 指标查询结果 ===\n\n");

            // 1. 查询 CPU 指标（如果告警与 CPU 相关）
            if (shouldQueryCPU(alertType, queryHint)) {
                String cpuMetric = metricsTools.queryMetric("cpu_usage", service);
                String cpuTrend = metricsTools.queryMetricRange("cpu_usage", service, DEFAULT_RANGE_MINUTES);
                results.append("【CPU 指标】\n").append(cpuMetric).append("\n\n");
                results.append("【CPU 趋势 (60分钟)】\n").append(cpuTrend).append("\n\n");
            }

            // 2. 查询内存指标（如果告警与内存相关）
            if (shouldQueryMemory(alertType, queryHint)) {
                String memMetric = metricsTools.queryMetric("memory_usage", service);
                String memTrend = metricsTools.queryMetricRange("memory_usage", service, DEFAULT_RANGE_MINUTES);
                results.append("【内存指标】\n").append(memMetric).append("\n\n");
                results.append("【内存趋势 (60分钟)】\n").append(memTrend).append("\n\n");
            }

            // 3. 查询延迟指标（如果告警与延迟相关）
            if (shouldQueryLatency(alertType, queryHint)) {
                String latencyMetric = metricsTools.queryMetric("http_request_duration_ms", service);
                String latencyTrend = metricsTools.queryMetricRange("http_request_duration_ms", service, DEFAULT_RANGE_MINUTES);
                results.append("【延迟指标】\n").append(latencyMetric).append("\n\n");
                results.append("【延迟趋势 (60分钟)】\n").append(latencyTrend).append("\n\n");
            }

            // 4. 查询错误率指标（如果告警与错误相关）
            if (shouldQueryError(alertType, queryHint)) {
                String errorMetric = metricsTools.queryMetric("http_requests_errors_total", service);
                String errorTrend = metricsTools.queryMetricRange("http_requests_errors_total", service, DEFAULT_RANGE_MINUTES);
                results.append("【错误率指标】\n").append(errorMetric).append("\n\n");
                results.append("【错误率趋势 (60分钟)】\n").append(errorTrend).append("\n\n");
            }

            // 如果没有匹配任何类型，查询默认指标
            if (!shouldQueryCPU(alertType, queryHint)
                    && !shouldQueryMemory(alertType, queryHint)
                    && !shouldQueryLatency(alertType, queryHint)
                    && !shouldQueryError(alertType, queryHint)) {
                // 查询通用指标
                String cpuMetric = metricsTools.queryMetric("cpu_usage", service);
                String memMetric = metricsTools.queryMetric("memory_usage", service);
                results.append("【通用 CPU 指标】\n").append(cpuMetric).append("\n\n");
                results.append("【通用内存指标】\n").append(memMetric).append("\n\n");
            }

            String metricsResult = results.toString();
            logger.info("MetricsAgent 查询完成，结果长度: {}", metricsResult.length());

            // 将结果存入共享数据
            context.putSharedData("metrics_result", metricsResult);
            context.putSharedData("metrics_query_time", dateTimeTools.getCurrentDateTime());

            return metricsResult;

        } catch (Exception e) {
            logger.error("MetricsAgent 执行失败", e);
            return "{\"status\":\"error\",\"message\":\"指标查询失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 判断是否需要查询 CPU 指标
     */
    private boolean shouldQueryCPU(String alertType, String queryHint) {
        if (alertType == null && queryHint == null) return false;
        String hint = (alertType + " " + (queryHint != null ? queryHint : "")).toLowerCase();
        return hint.contains("cpu") || hint.contains("load");
    }

    /**
     * 判断是否需要查询内存指标
     */
    private boolean shouldQueryMemory(String alertType, String queryHint) {
        if (alertType == null && queryHint == null) return false;
        String hint = (alertType + " " + (queryHint != null ? queryHint : "")).toLowerCase();
        return hint.contains("memory") || hint.contains("mem") || hint.contains("oom") || hint.contains("jvm");
    }

    /**
     * 判断是否需要查询延迟指标
     */
    private boolean shouldQueryLatency(String alertType, String queryHint) {
        if (alertType == null && queryHint == null) return false;
        String hint = (alertType + " " + (queryHint != null ? queryHint : "")).toLowerCase();
        return hint.contains("latency") || hint.contains("slow") || hint.contains("response")
                || hint.contains("delay") || hint.contains("timeout");
    }

    /**
     * 判断是否需要查询错误率指标
     */
    private boolean shouldQueryError(String alertType, String queryHint) {
        if (alertType == null && queryHint == null) return false;
        String hint = (alertType + " " + (queryHint != null ? queryHint : "")).toLowerCase();
        return hint.contains("error") || hint.contains("exception") || hint.contains("500")
                || hint.contains("fail");
    }

    /**
     * 构建提示词
     */
    public String buildPrompt(AiOpsContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 MetricsAgent，专门负责查询和分析系统指标信息。\n");
        prompt.append("当前任务上下文：\n");

        if (context.getTenantId() != null) {
            prompt.append("- 租户/系统：").append(context.getTenantId()).append("\n");
        }
        if (context.getServiceId() != null) {
            prompt.append("- 服务：").append(context.getServiceId()).append("\n");
        }
        if (context.getAlertName() != null) {
            prompt.append("- 告警名称：").append(context.getAlertName()).append("\n");
        }

        prompt.append("\n请查询相关的指标信息。");
        return prompt.toString();
    }
}