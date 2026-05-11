package org.example.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.dto.AiOpsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * LogsAgent - 日志查询 Agent
 * 专门负责从 CLS（云日志服务）查询日志
 * 数据维度：服务实例级别（serviceId + instanceId）
 */
@Component
public class LogsAgent {

    private static final Logger logger = LoggerFactory.getLogger(LogsAgent.class);

    public static final String AGENT_NAME = "logs_agent";
    public static final String OUTPUT_KEY = "logs_result";

    private static final String DEFAULT_REGION = "ap-guangzhou";
    private static final String DEFAULT_LOG_TOPIC = "application-logs";

    @Autowired
    private QueryLogsTools queryLogsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    /**
     * 执行日志查询
     */
    public String execute(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks) {
        logger.info("LogsAgent 开始执行，tenantId: {}, serviceId: {}, instanceId: {}",
                context.getTenantId(), context.getServiceId(), context.getInstanceId());

        try {
            String region = DEFAULT_REGION;
            String logTopic = DEFAULT_LOG_TOPIC;
            String query = buildQueryFromContext(context);

            // 查询可用的日志主题
            String topicsResult = queryLogsTools.getAvailableLogTopics();
            logger.debug("获取到日志主题列表: {}", topicsResult);

            // 执行日志查询
            String logsResult = queryLogsTools.queryLogs(region, logTopic, query, 50);
            logger.info("LogsAgent 查询到日志结果，长度: {}", logsResult.length());

            // 将结果存入共享数据
            context.putSharedData("logs_result", logsResult);
            context.putSharedData("logs_region", region);
            context.putSharedData("logs_topic", logTopic);

            return logsResult;
        } catch (Exception e) {
            logger.error("LogsAgent 执行失败", e);
            return "{\"status\":\"error\",\"message\":\"日志查询失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 根据上下文构建查询条件
     */
    private String buildQueryFromContext(AiOpsContext context) {
        StringBuilder query = new StringBuilder();

        // 1. 添加服务名查询
        if (context.getServiceId() != null) {
            query.append("service:").append(context.getServiceId());
        }

        // 2. 根据告警类型添加相关查询
        if (context.getAlertName() != null) {
            String alertNameLower = context.getAlertName().toLowerCase();
            if (alertNameLower.contains("cpu")) {
                if (query.length() > 0) query.append(" AND ");
                query.append("cpu");
            } else if (alertNameLower.contains("memory") || alertNameLower.contains("oom")) {
                if (query.length() > 0) query.append(" AND ");
                query.append("(memory OR oom)");
            } else if (alertNameLower.contains("disk")) {
                if (query.length() > 0) query.append(" AND ");
                query.append("disk");
            } else if (alertNameLower.contains("error") || alertNameLower.contains("500")) {
                if (query.length() > 0) query.append(" AND ");
                query.append("level:ERROR");
            }
        }

        // 3. 添加查询策略提示（如果存在）
        String queryHint = context.getSharedData("query_hint", "");
        if (queryHint != null && !queryHint.isEmpty()) {
            if (query.length() > 0) query.append(" AND ");
            query.append(queryHint);
        }

        return query.toString();
    }

    /**
     * 获取日志主题列表
     */
    public String getAvailableLogTopics() {
        return queryLogsTools.getAvailableLogTopics();
    }

    /**
     * 构建提示词
     */
    public String buildPrompt(AiOpsContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 LogsAgent，专门负责查询和分析日志信息。\n");
        prompt.append("当前任务上下文：\n");

        if (context.getTenantId() != null) {
            prompt.append("- 租户/系统：").append(context.getTenantId()).append("\n");
        }
        if (context.getServiceId() != null) {
            prompt.append("- 服务：").append(context.getServiceId()).append("\n");
        }
        if (context.getInstanceId() != null) {
            prompt.append("- 实例：").append(context.getInstanceId()).append("\n");
        }
        if (context.getAlertName() != null) {
            prompt.append("- 告警名称：").append(context.getAlertName()).append("\n");
        }

        prompt.append("\n请查询相关的日志信息。");
        return prompt.toString();
    }
}