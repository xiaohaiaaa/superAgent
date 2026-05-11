package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.example.dto.MetricPoint;
import org.example.dto.MetricTimeSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Prometheus 指标查询工具
 * 支持即时查询和时间范围查询
 */
@Component
public class QueryPrometheusMetricsTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryPrometheusMetricsTools.class);

    public static final String TOOL_QUERY_METRIC = "queryMetric";
    public static final String TOOL_QUERY_METRIC_RANGE = "queryMetricRange";

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
        logger.info("✅ QueryPrometheusMetricsTools 初始化成功, Prometheus URL: {}, Mock模式: {}",
                prometheusBaseUrl, mockEnabled);
    }

    /**
     * 查询指标当前值（即时查询）
     *
     * @param metricName 指标名称，如 cpu_usage, memory_usage
     * @param service 服务名称，如 payment-service
     * @return JSON 格式的指标结果
     */
    @Tool(description = "Query current value of a specific metric from Prometheus. " +
            "Use this to get the latest value of CPU, memory, disk, or other system metrics. " +
            "Parameters: metricName (e.g., cpu_usage, memory_usage), service (e.g., payment-service)")
    public String queryMetric(String metricName, String service) {
        logger.info("查询指标即时值: metricName={}, service={}", metricName, service);

        try {
            if (mockEnabled) {
                return buildMockMetricResult(metricName, service);
            }

            // 构建 PromQL 查询
            String query = buildPromQL(metricName, service);
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = prometheusBaseUrl + "/api/v1/query?query=" + encodedQuery;

            logger.debug("请求 Prometheus API: {}", url);
            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return buildErrorResponse("Prometheus API 请求失败: " + response.code());
                }

                String body = response.body().string();
                return parseInstantQueryResult(body, metricName, service);
            }

        } catch (Exception e) {
            logger.error("查询指标失败", e);
            return buildErrorResponse("查询指标失败: " + e.getMessage());
        }
    }

    /**
     * 查询指标时间范围（趋势查询）
     *
     * @param metricName 指标名称
     * @param service 服务名称
     * @param rangeMinutes 时间范围（分钟），默认 60
     * @return JSON 格式的时间序列结果
     */
    @Tool(description = "Query metric time series data for trend analysis. " +
            "Use this to analyze how a metric changes over time, detect anomalies, or view historical patterns. " +
            "Parameters: metricName (e.g., cpu_usage), service (e.g., payment-service), rangeMinutes (default 60)")
    public String queryMetricRange(String metricName, String service, Integer rangeMinutes) {
        int range = (rangeMinutes == null || rangeMinutes <= 0) ? 60 : rangeMinutes;
        logger.info("查询指标时间范围: metricName={}, service={}, range={}min", metricName, service, range);

        try {
            if (mockEnabled) {
                return buildMockMetricRange(metricName, service, range);
            }

            // 计算时间范围
            long endTime = Instant.now().getEpochSecond();
            long startTime = endTime - (range * 60L);
            int step = Math.max(15, range * 60 / 100); // 至少 15 秒 step

            // 构建 PromQL 查询
            String query = buildPromQL(metricName, service);
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = prometheusBaseUrl + "/api/v1/query_range?query=" + encodedQuery
                    + "&start=" + startTime + "&end=" + endTime + "&step=" + step;

            logger.debug("请求 Prometheus API: {}", url);
            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return buildErrorResponse("Prometheus API 请求失败: " + response.code());
                }

                String body = response.body().string();
                return parseRangeQueryResult(body, metricName, service, range, step);
            }

        } catch (Exception e) {
            logger.error("查询指标时间范围失败", e);
            return buildErrorResponse("查询指标时间范围失败: " + e.getMessage());
        }
    }

    /**
     * 构建 PromQL 查询表达式
     */
    private String buildPromQL(String metricName, String service) {
        if (service == null || service.isEmpty()) {
            return metricName;
        }
        return metricName + "{service=\"" + service + "\"}";
    }

    /**
     * 解析即时查询结果
     */
    private String parseInstantQueryResult(String responseBody, String metricName, String service) {
        try {
            // 简化处理：实际应该解析 Prometheus API 返回的 JSON 结构
            // 返回格式: {"status":"success","data":{"resultType":"vector","result":[{"value":[1234567.89,"92"]}]}}
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            String status = (String) result.get("status");

            if (!"success".equals(status)) {
                return buildErrorResponse("Prometheus 查询失败");
            }

            MetricTimeSeries ts = new MetricTimeSeries();
            ts.setMetricName(metricName);
            ts.setService(service);
            ts.setSuccess(true);

            Map<String, Object> data = (Map<String, Object>) result.get("data");
            List<Object> results = (List<Object>) data.get("result");

            if (results != null && !results.isEmpty()) {
                Object firstResult = results.get(0);
                if (firstResult instanceof Map) {
                    Map<String, Object> resultMap = (Map<String, Object>) firstResult;
                    List<Object> value = (List<Object>) resultMap.get("value");
                    if (value != null && value.size() >= 2) {
                        double val = Double.parseDouble(value.get(1).toString());
                        ts.setCurrentValue(val);
                        ts.getPoints().add(new MetricPoint((long) Double.parseDouble(value.get(0).toString()), val));
                    }
                }
            }

            ts.setMessage("查询成功");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ts);

        } catch (Exception e) {
            logger.error("解析即时查询结果失败", e);
            return buildErrorResponse("解析结果失败: " + e.getMessage());
        }
    }

    /**
     * 解析范围查询结果
     */
    private String parseRangeQueryResult(String responseBody, String metricName, String service,
                                         int rangeMinutes, int stepSeconds) {
        try {
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            String status = (String) result.get("status");

            if (!"success".equals(status)) {
                return buildErrorResponse("Prometheus 查询失败");
            }

            MetricTimeSeries ts = new MetricTimeSeries();
            ts.setMetricName(metricName);
            ts.setService(service);
            ts.setRangeSeconds(rangeMinutes * 60L);
            ts.setStepSeconds(stepSeconds);
            ts.setSuccess(true);

            Map<String, Object> data = (Map<String, Object>) result.get("data");
            List<Object> results = (List<Object>) data.get("result");

            if (results != null && !results.isEmpty()) {
                Object firstResult = results.get(0);
                if (firstResult instanceof Map) {
                    Map<String, Object> resultMap = (Map<String, Object>) firstResult;
                    List<Object> values = (List<Object>) resultMap.get("values");

                    if (values != null) {
                        for (Object v : values) {
                            if (v instanceof List) {
                                List<Object> valueList = (List<Object>) v;
                                if (valueList.size() >= 2) {
                                    long timestamp = (long) Double.parseDouble(valueList.get(0).toString());
                                    double val = Double.parseDouble(valueList.get(1).toString());
                                    ts.getPoints().add(new MetricPoint(timestamp, val));
                                }
                            }
                        }
                    }
                }
            }

            ts.calculateStats();
            ts.setMessage(String.format("查询成功，获取 %d 个数据点", ts.getPoints().size()));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ts);

        } catch (Exception e) {
            logger.error("解析范围查询结果失败", e);
            return buildErrorResponse("解析结果失败: " + e.getMessage());
        }
    }

    // ==================== Mock 数据生成 ====================

    private String buildMockMetricResult(String metricName, String service) {
        MetricTimeSeries ts = new MetricTimeSeries();
        ts.setMetricName(metricName);
        ts.setService(service);
        ts.setSuccess(true);

        double value = generateMockValue(metricName);
        ts.setCurrentValue(value);
        ts.getPoints().add(new MetricPoint(Instant.now().getEpochSecond(), value));
        ts.setMessage("查询成功");

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ts);
        } catch (Exception e) {
            return buildErrorResponse("序列化失败");
        }
    }

    private String buildMockMetricRange(String metricName, String service, int rangeMinutes) {
        MetricTimeSeries ts = new MetricTimeSeries();
        ts.setMetricName(metricName);
        ts.setService(service);
        ts.setRangeSeconds(rangeMinutes * 60L);
        ts.setStepSeconds(15);
        ts.setSuccess(true);

        // 生成时间序列数据
        Instant now = Instant.now();
        int numPoints = (rangeMinutes * 60) / 15; // 15 秒一个点
        double baseValue = generateMockValue(metricName);

        for (int i = numPoints; i >= 0; i--) {
            long timestamp = now.minus(i * 15, ChronoUnit.SECONDS).getEpochSecond();
            // 添加一些波动
            double variation = (Math.random() - 0.5) * 10;
            double value = Math.max(0, Math.min(100, baseValue + variation));
            ts.getPoints().add(new MetricPoint(timestamp, value));
        }

        ts.calculateStats();
        ts.setMessage(String.format("Mock数据: 查询 %d 分钟时间范围, 获取 %d 个数据点",
                rangeMinutes, ts.getPoints().size()));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ts);
        } catch (Exception e) {
            return buildErrorResponse("序列化失败");
        }
    }

    private double generateMockValue(String metricName) {
        if (metricName == null) return 50.0;

        String name = metricName.toLowerCase();
        if (name.contains("cpu")) {
            return 75.0 + Math.random() * 20; // 75-95%
        } else if (name.contains("memory") || name.contains("mem")) {
            return 70.0 + Math.random() * 25; // 70-95%
        } else if (name.contains("disk")) {
            return 60.0 + Math.random() * 30; // 60-90%
        } else if (name.contains("request") || name.contains("latency")) {
            return 100.0 + Math.random() * 500; // 100-600ms
        } else if (name.contains("error") || name.contains("fail")) {
            return Math.random() * 5; // 0-5%
        } else {
            return 50.0 + Math.random() * 30; // 默认 50-80
        }
    }

    private String buildErrorResponse(String message) {
        MetricTimeSeries ts = new MetricTimeSeries();
        ts.setSuccess(false);
        ts.setMessage(message);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ts);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + message + "\"}";
        }
    }
}