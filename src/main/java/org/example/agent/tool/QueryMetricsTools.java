package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.example.dto.PrometheusAlert;
import org.example.dto.PrometheusAlertsOutput;
import org.example.dto.PrometheusAlertsResult;
import org.example.dto.SimplifiedAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Prometheus 告警查询工具
 */
@Component
public class QueryMetricsTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryMetricsTools.class);

    public static final String TOOL_QUERY_PROMETHEUS_ALERTS = "queryPrometheusAlerts";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${prometheus.base-url}")
    private String prometheusBaseUrl;

    @Value("${prometheus.timeout:10}")
    private int timeout;

    @Value("${prometheus.mock-enabled:false}")
    private boolean mockEnabled;

    private OkHttpClient httpClient;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .readTimeout(Duration.ofSeconds(timeout))
                .build();
        logger.info("✅ QueryMetricsTools 初始化成功, Prometheus URL: {}, Mock模式: {}", prometheusBaseUrl, mockEnabled);
    }

    @Tool(description = "Query active alerts from Prometheus alerting system. " +
            "This tool retrieves all currently active/firing alerts including their labels, annotations, state, and values. " +
            "Use this tool when you need to check what alerts are currently firing, investigate alert conditions, or monitor alert status.")
    public String queryPrometheusAlerts() {
        logger.info("开始查询 Prometheus 活动告警, Mock模式: {}", mockEnabled);

        try {
            List<SimplifiedAlert> simplifiedAlerts;

            if (mockEnabled) {
                simplifiedAlerts = buildMockAlerts();
                logger.info("使用 Mock 数据，返回 {} 个模拟告警", simplifiedAlerts.size());
            } else {
                PrometheusAlertsResult result = fetchPrometheusAlerts();

                if (!"success".equals(result.getStatus())) {
                    return buildErrorResponse("Prometheus API 返回非成功状态: " + result.getStatus(), result.getError());
                }

                Set<String> seenAlertNames = new HashSet<>();
                simplifiedAlerts = new ArrayList<>();

                for (PrometheusAlert alert : result.getData().getAlerts()) {
                    String alertName = alert.getLabels().get("alertname");

                    if (seenAlertNames.contains(alertName)) {
                        continue;
                    }

                    seenAlertNames.add(alertName);

                    SimplifiedAlert simplified = new SimplifiedAlert();
                    simplified.setAlertName(alertName);
                    simplified.setDescription(alert.getAnnotations().getOrDefault("description", ""));
                    simplified.setState(alert.getState());
                    simplified.setActiveAt(alert.getActiveAt());
                    simplified.setDuration(calculateDuration(alert.getActiveAt()));

                    simplifiedAlerts.add(simplified);
                }
            }

            PrometheusAlertsOutput output = new PrometheusAlertsOutput();
            output.setSuccess(true);
            output.setAlerts(simplifiedAlerts);
            output.setMessage(String.format("成功检索到 %d 个活动告警", simplifiedAlerts.size()));

            String jsonResult = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            logger.info("Prometheus 告警查询完成: 找到 {} 个告警", simplifiedAlerts.size());

            return jsonResult;

        } catch (Exception e) {
            logger.error("查询 Prometheus 告警失败", e);
            return buildErrorResponse("查询失败", e.getMessage());
        }
    }

    private List<SimplifiedAlert> buildMockAlerts() {
        List<SimplifiedAlert> alerts = new ArrayList<>();
        Instant now = Instant.now();

        SimplifiedAlert cpuAlert = new SimplifiedAlert();
        cpuAlert.setAlertName("HighCPUUsage");
        cpuAlert.setDescription("服务 payment-service 的 CPU 使用率持续超过 80%，当前值为 92%。" +
                "实例: pod-payment-service-7d8f9c6b5-x2k4m，命名空间: production");
        cpuAlert.setState("firing");
        Instant cpuActiveAt = now.minus(25, ChronoUnit.MINUTES);
        cpuAlert.setActiveAt(cpuActiveAt.toString());
        cpuAlert.setDuration(calculateDuration(cpuActiveAt.toString()));
        alerts.add(cpuAlert);

        SimplifiedAlert memoryAlert = new SimplifiedAlert();
        memoryAlert.setAlertName("HighMemoryUsage");
        memoryAlert.setDescription("服务 order-service 的内存使用率持续超过 85%，当前值为 91%。" +
                "JVM堆内存使用: 3.8GB/4GB，可能存在内存泄漏风险。" +
                "实例: pod-order-service-5c7d8e9f1-m3n2p，命名空间: production");
        memoryAlert.setState("firing");
        Instant memoryActiveAt = now.minus(15, ChronoUnit.MINUTES);
        memoryAlert.setActiveAt(memoryActiveAt.toString());
        memoryAlert.setDuration(calculateDuration(memoryActiveAt.toString()));
        alerts.add(memoryAlert);

        SimplifiedAlert slowAlert = new SimplifiedAlert();
        slowAlert.setAlertName("SlowResponse");
        slowAlert.setDescription("服务 user-service 的 P99 响应时间持续超过 3 秒，当前值为 4.2 秒。" +
                "受影响接口: /api/v1/users/profile, /api/v1/users/orders。" +
                "可能原因：数据库慢查询或下游服务延迟");
        slowAlert.setState("firing");
        Instant slowActiveAt = now.minus(10, ChronoUnit.MINUTES);
        slowAlert.setActiveAt(slowActiveAt.toString());
        slowAlert.setDuration(calculateDuration(slowActiveAt.toString()));
        alerts.add(slowAlert);

        return alerts;
    }

    private PrometheusAlertsResult fetchPrometheusAlerts() throws Exception {
        String apiUrl = prometheusBaseUrl + "/api/v1/alerts";
        logger.debug("请求 Prometheus API: {}", apiUrl);

        Request request = new Request.Builder()
                .url(apiUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP 请求失败: " + response.code());
            }

            String responseBody = response.body().string();
            return objectMapper.readValue(responseBody, PrometheusAlertsResult.class);
        }
    }

    private String calculateDuration(String activeAtStr) {
        try {
            Instant activeAt = Instant.parse(activeAtStr);
            Duration duration = Duration.between(activeAt, Instant.now());

            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;

            if (hours > 0) {
                return String.format("%dh%dm%ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm%ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        } catch (Exception e) {
            logger.warn("解析时间失败: {}", activeAtStr, e);
            return "unknown";
        }
    }

    private String buildErrorResponse(String message, String error) {
        try {
            PrometheusAlertsOutput output = new PrometheusAlertsOutput();
            output.setSuccess(false);
            output.setMessage(message);
            output.setError(error);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            return String.format("{\"success\":false,\"message\":\"%s\",\"error\":\"%s\"}", message, error);
        }
    }
}