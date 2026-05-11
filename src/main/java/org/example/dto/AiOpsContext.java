package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Ops 多租户上下文 DTO
 * 用于在多 Agent 协作时传递租户、服务、实例等维度信息
 */
@Data
@Schema(description = "AI Ops 多租户上下文")
public class AiOpsContext {

    @Schema(description = "租户/系统 ID", example = "ecommerce-system")
    private String tenantId;

    @Schema(description = "服务 ID", example = "payment-service")
    private String serviceId;

    @Schema(description = "服务实例 ID（可选）", example = "pod-payment-service-7d8f9c6b5-x2k4m")
    private String instanceId;

    @Schema(description = "告警名称（可选）", example = "HighCPUUsage")
    private String alertName;

    @Schema(description = "Agent 间共享数据")
    private Map<String, Object> sharedData;

    public AiOpsContext() {
        this.sharedData = new HashMap<>();
    }

    public AiOpsContext(String tenantId) {
        this();
        this.tenantId = tenantId;
    }

    public AiOpsContext(String tenantId, String serviceId) {
        this(tenantId);
        this.serviceId = serviceId;
    }

    /**
     * 添加共享数据
     */
    public void putSharedData(String key, Object value) {
        if (this.sharedData == null) {
            this.sharedData = new HashMap<>();
        }
        this.sharedData.put(key, value);
    }

    /**
     * 获取共享数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key) {
        if (this.sharedData == null) {
            return null;
        }
        return (T) this.sharedData.get(key);
    }

    /**
     * 获取共享数据，带默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key, T defaultValue) {
        T value = getSharedData(key);
        return value != null ? value : defaultValue;
    }
}