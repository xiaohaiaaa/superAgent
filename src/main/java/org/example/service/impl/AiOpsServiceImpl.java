package org.example.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.agent.supervisor.AiOpsSupervisorAgent;
import org.example.dto.AiOpsContext;
import org.example.service.AiOpsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AI Ops 服务实现
 * 采用多 Agent 协作架构：
 * - AiOpsSupervisorAgent（总调度）
 * - AlertAgent（告警查询）
 * - LogsAgent（日志查询）
 * - MetricsAgent（指标查询）
 * - DocsAgent（知识库检索）
 * - ReporterAgent（报告生成）
 */
@Service
public class AiOpsServiceImpl implements AiOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsServiceImpl.class);

    @Autowired
    private AiOpsSupervisorAgent supervisorAgent;

    @Override
    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) throws Exception {
        logger.info("=== 旧接口：executeAiOpsAnalysis（兼容模式）===");

        // 创建默认上下文
        AiOpsContext context = new AiOpsContext("default-tenant");

        // 执行新架构
        String report = supervisorAgent.execute(chatModel, context, toolCallbacks);

        // 转换为 OverAllState 返回（兼容旧接口）
        OverAllState state = buildOverAllState(report);
        return Optional.of(state);
    }

    @Override
    public String executeAiOpsAnalysisWithContext(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks, AiOpsContext context) {
        logger.info("=== 新接口：executeAiOpsAnalysisWithContext ===");
        logger.info("租户: {}, 服务: {}, 告警: {}",
                context.getTenantId(), context.getServiceId(), context.getAlertName());

        try {
            // 调用 SupervisorAgent 执行多 Agent 协作
            String report = supervisorAgent.execute(chatModel, context, toolCallbacks);
            logger.info("多 Agent 协作完成，报告长度: {}", report.length());
            return report;
        } catch (Exception e) {
            logger.error("多 Agent 协作执行失败", e);
            return buildErrorReport(e.getMessage());
        }
    }

    @Override
    public Optional<String> extractFinalReport(OverAllState state) {
        if (state == null) {
            logger.warn("OverAllState 为空，无法提取报告");
            return Optional.empty();
        }

        try {
            // 尝试从 state 中提取报告
            Object reportObj = state.value("report");
            if (reportObj != null) {
                String report = unwrapOptional(reportObj.toString());
                logger.info("从 OverAllState 提取到报告，长度: {}", report.length());
                return Optional.of(report);
            }

            // 尝试从 planner_plan 字段提取（AssistantMessage）
            Object plannerObj = state.value("planner_plan");
            if (plannerObj != null) {
                String report = unwrapOptional(plannerObj.toString());
                logger.info("从 OverAllState.planner_plan 提取到报告，长度: {}", report.length());
                return Optional.of(report);
            }

            logger.warn("OverAllState 中未找到报告内容");
            return Optional.empty();

        } catch (Exception e) {
            logger.error("提取报告失败", e);
            return Optional.empty();
        }
    }

    /**
     * 去除 Optional[...] 包装
     */
    private String unwrapOptional(String value) {
        if (value != null && value.startsWith("Optional[")) {
            return value.substring("Optional[".length(), value.length() - 1);
        }
        return value;
    }

    /**
     * 构建 OverAllState（用于兼容旧接口）
     */
    private OverAllState buildOverAllState(String report) {
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("report", report);
        stateMap.put("status", "completed");

        AssistantMessage message = new AssistantMessage(report);
        stateMap.put("planner_plan", message);

        return new OverAllState(stateMap);
    }

    /**
     * 构建错误报告
     */
    private String buildErrorReport(String errorMessage) {
        StringBuilder report = new StringBuilder();
        report.append("# 告警分析报告\n\n");
        report.append("---\n\n");
        report.append("## ⚠️ 执行失败\n\n");
        report.append("在执行多 Agent 协作过程中发生错误：\n\n");
        report.append("```\n").append(errorMessage).append("\n```\n\n");
        report.append("---\n\n");
        report.append("## 📊 结论\n\n");
        report.append("### 整体评估\n");
        report.append("由于执行过程中遇到错误，无法完成完整的告警分析。\n");
        report.append("\n### 后续建议\n");
        report.append("1. 检查系统日志获取更多错误详情\n");
        report.append("2. 确认各数据源（Prometheus、CLS、Milvus）连接正常\n");
        report.append("3. 如问题持续，请联系运维人员\n");

        return report.toString();
    }
}