package org.example.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.agent.tool.InternalDocsTools;
import org.example.dto.AiOpsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DocsAgent - 知识库检索 Agent
 * 专门负责从内部知识库（RAG）检索相关文档
 * 数据维度：系统级别（systemId/tenantId）
 */
@Component
public class DocsAgent {

    private static final Logger logger = LoggerFactory.getLogger(DocsAgent.class);

    public static final String AGENT_NAME = "docs_agent";
    public static final String OUTPUT_KEY = "docs_result";

    @Autowired
    private InternalDocsTools internalDocsTools;

    /**
     * 执行知识库检索
     */
    public String execute(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks) {
        logger.info("DocsAgent 开始执行，tenantId: {}, serviceId: {}, alertName: {}",
                context.getTenantId(), context.getServiceId(), context.getAlertName());

        try {
            // 构建检索 query
            String searchQuery = buildSearchQuery(context);

            // 执行知识库检索
            String docsResult = internalDocsTools.queryInternalDocs(searchQuery);
            logger.info("DocsAgent 查询到文档结果，长度: {}", docsResult.length());

            // 将结果存入共享数据
            context.putSharedData("docs_result", docsResult);
            context.putSharedData("docs_query", searchQuery);

            return docsResult;
        } catch (Exception e) {
            logger.error("DocsAgent 执行失败", e);
            return "{\"status\":\"error\",\"message\":\"知识库检索失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 根据上下文构建检索 query
     */
    private String buildSearchQuery(AiOpsContext context) {
        StringBuilder query = new StringBuilder();

        // 优先使用告警名称作为检索词
        if (context.getAlertName() != null) {
            query.append(context.getAlertName());
        }

        // 添加服务信息
        if (context.getServiceId() != null) {
            if (query.length() > 0) {
                query.append(" ");
            }
            query.append(context.getServiceId());
        }

        // 添加租户/系统信息
        if (context.getTenantId() != null) {
            if (query.length() > 0) {
                query.append(" ");
            }
            query.append(context.getTenantId());
        }

        // 如果没有任何检索词，使用默认 query
        if (query.length() == 0) {
            query.append("系统告警 故障排查");
        }

        return query.toString();
    }

    /**
     * 构建提示词
     */
    public String buildPrompt(AiOpsContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 DocsAgent，专门负责从内部知识库检索相关文档。\n");
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

        prompt.append("\n请检索与当前告警相关的处理文档和运维手册。");
        return prompt.toString();
    }
}