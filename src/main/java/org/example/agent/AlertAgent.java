package org.example.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.agent.tool.QueryMetricsTools;
import org.example.dto.AiOpsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AlertAgent - 告警分析 Agent
 * 专门负责从 Prometheus 查询告警信息
 * 数据维度：服务级别（serviceId）
 */
@Component
public class AlertAgent {

    private static final Logger logger = LoggerFactory.getLogger(AlertAgent.class);

    public static final String AGENT_NAME = "alert_agent";
    public static final String OUTPUT_KEY = "alert_result";

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    /**
     * 执行告警查询
     */
    public String execute(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks) {
        logger.info("AlertAgent 开始执行，tenantId: {}, serviceId: {}",
                context.getTenantId(), context.getServiceId());

        try {
            // 使用 QueryMetricsTools 查询告警
            String alertResult = queryMetricsTools.queryPrometheusAlerts();
            logger.info("AlertAgent 查询到告警结果，长度: {}", alertResult.length());

            // 将结果存入共享数据
            context.putSharedData("alert_result", alertResult);

            // 如果有告警名称，单独存储该告警详情
            if (context.getAlertName() != null) {
                context.putSharedData("current_alert_name", context.getAlertName());
            }

            return alertResult;
        } catch (Exception e) {
            logger.error("AlertAgent 执行失败", e);
            return "{\"status\":\"error\",\"message\":\"告警查询失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 构建提示词（用于后续可能的 LLM 增强）
     */
    public String buildPrompt(AiOpsContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 AlertAgent，专门负责查询和分析告警信息。\n");
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

        prompt.append("\n请查询相关的告警信息。");
        return prompt.toString();
    }
}