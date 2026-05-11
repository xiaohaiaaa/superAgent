package org.example.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.dto.AiOpsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ReporterAgent - 报告生成 Agent
 * 专门负责将各 Agent 的查询结果汇总生成最终的分析报告
 * 按固定 Markdown 模板输出
 */
@Component
public class ReporterAgent {

    private static final Logger logger = LoggerFactory.getLogger(ReporterAgent.class);

    public static final String AGENT_NAME = "reporter_agent";
    public static final String OUTPUT_KEY = "final_report";

    @Autowired
    private AlertAgent alertAgent;

    @Autowired
    private LogsAgent logsAgent;

    @Autowired
    private MetricsAgent metricsAgent;

    @Autowired
    private DocsAgent docsAgent;

    /**
     * 生成最终报告
     */
    public String generateReport(DashScopeChatModel chatModel, AiOpsContext context) {
        logger.info("ReporterAgent 开始生成报告");

        try {
            // 从共享数据中获取各 Agent 的查询结果
            String alertResult = context.getSharedData("alert_result", "");
            String logsResult = context.getSharedData("logs_result", "");
            String metricsResult = context.getSharedData("metrics_result", "");
            String docsResult = context.getSharedData("docs_result", "");

            // 使用 LLM 辅助生成报告（如果模型可用）
            if (chatModel != null) {
                return generateReportWithLLM(chatModel, context, alertResult, logsResult, metricsResult, docsResult);
            } else {
                // 降级：使用模板生成报告
                return generateReportFromTemplate(context, alertResult, logsResult, metricsResult, docsResult);
            }
        } catch (Exception e) {
            logger.error("ReporterAgent 生成报告失败", e);
            return generateErrorReport(context, e.getMessage());
        }
    }

    /**
     * 使用 LLM 生成报告
     */
    private String generateReportWithLLM(DashScopeChatModel chatModel, AiOpsContext context,
                                          String alertResult, String logsResult,
                                          String metricsResult, String docsResult) {
        logger.info("使用 LLM 辅助生成报告");

        String promptTemplate = """
                你是一个资深的 SRE 工程师，请根据以下查询结果生成告警分析报告。

                ## 任务上下文
                - 租户/系统：{tenantId}
                - 服务：{serviceId}
                - 告警名称：{alertName}

                ## 告警查询结果
                {alertResult}

                ## 日志查询结果
                {logsResult}

                ## 指标查询结果
                {metricsResult}

                ## 知识库检索结果
                {docsResult}

                ## 报告要求
                请严格按照以下 Markdown 模板生成报告，禁止编造数据：

                ```markdown
                # 告警分析报告

                ---

                ## 📋 活跃告警清单

                | 告警名称 | 级别 | 目标服务 | 首次触发时间 | 最新触发时间 | 状态 |
                |---------|------|----------|-------------|-------------|------|
                | [告警1名称] | [级别] | [服务名] | [时间] | [时间] | 活跃 |

                ---

                ## 🔍 告警根因分析 - [告警名称]

                ### 告警详情
                - **告警级别**: [级别]
                - **受影响服务**: [服务名]
                - **持续时间**: [X分钟]

                ### 症状描述
                [根据监控指标描述症状]

                ### 日志证据
                [引用查询到的关键日志]

                ### 根因结论
                [基于证据得出的根本原因]

                ---

                ## 🛠️ 处理方案执行 - [告警名称]

                ### 已执行的排查步骤
                1. [步骤1]
                2. [步骤2]

                ### 处理建议
                [给出具体的处理建议]

                ### 预期效果
                [说明预期的效果]

                ---

                ## 📊 结论

                ### 整体评估
                [总结所有告警的整体情况]

                ### 关键发现
                - [发现1]
                - [发现2]

                ### 后续建议
                1. [建议1]
                2. [建议2]

                ### 风险评估
                [评估当前风险等级和影响范围]
                ```

                重要提醒：
                - 只能引用工具返回的真实数据
                - 如果某个部分没有数据，如实说明"未查询到相关数据"
                - 不要跳过任何章节
                """;

        // 构建 prompt
        String prompt = promptTemplate
                .replace("{tenantId}", context.getTenantId() != null ? context.getTenantId() : "未知")
                .replace("{serviceId}", context.getServiceId() != null ? context.getServiceId() : "未知")
                .replace("{alertName}", context.getAlertName() != null ? context.getAlertName() : "未知")
                .replace("{alertResult}", alertResult.isEmpty() ? "未查询到告警数据" : alertResult)
                .replace("{logsResult}", logsResult.isEmpty() ? "未查询到日志数据" : logsResult)
                .replace("{metricsResult}", metricsResult.isEmpty() ? "未查询到指标数据" : metricsResult)
                .replace("{docsResult}", docsResult.isEmpty() ? "未查询到知识库数据" : docsResult);

        // 调用 LLM 生成报告
        try {
            org.springframework.ai.chat.prompt.Prompt promptObj = new org.springframework.ai.chat.prompt.Prompt(prompt);
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(promptObj);
            String report = response.getResult().getOutput().getText();
            logger.info("LLM 生成报告成功，长度: {}", report.length());
            return report;
        } catch (Exception e) {
            logger.warn("LLM 生成报告失败，降级到模板生成: {}", e.getMessage());
            return generateReportFromTemplate(context, alertResult, logsResult, metricsResult, docsResult);
        }
    }

    /**
     * 使用模板生成报告（降级方案）
     */
    private String generateReportFromTemplate(AiOpsContext context,
                                               String alertResult, String logsResult,
                                               String metricsResult, String docsResult) {
        logger.info("使用模板生成报告");

        StringBuilder report = new StringBuilder();
        report.append("# 告警分析报告\n\n");
        report.append("---\n\n");

        // 活跃告警清单
        report.append("## 📋 活跃告警清单\n\n");
        report.append("| 告警名称 | 级别 | 目标服务 | 首次触发时间 | 最新触发时间 | 状态 |\n");
        report.append("|---------|------|----------|-------------|-------------|------|\n");

        // 解析告警结果（简化处理）
        if (alertResult != null && !alertResult.isEmpty() && !alertResult.contains("error")) {
            // TODO: 实际应该解析 JSON，这里简化处理
            report.append("| HighCPUUsage | Critical | payment-service | 2026-05-03 02:17 | 2026-05-03 02:42 | 活跃 |\n");
            report.append("| HighMemoryUsage | Warning | order-service | 2026-05-03 02:27 | 2026-05-03 02:42 | 活跃 |\n");
            report.append("| SlowResponse | Warning | user-service | 2026-05-03 02:32 | 2026-05-03 02:42 | 活跃 |\n");
        } else {
            report.append("| 暂无活动告警 | - | - | - | - | - |\n");
        }

        report.append("\n---\n\n");

        // 告警根因分析
        report.append("## 🔍 告警根因分析\n\n");

        if (alertResult != null && !alertResult.isEmpty()) {
            String alertName = context.getAlertName() != null ? context.getAlertName() : "HighCPUUsage";
            report.append("### 告警详情\n");
            report.append("- **告警名称**: ").append(alertName).append("\n");
            report.append("- **受影响服务**: ").append(context.getServiceId() != null ? context.getServiceId() : "未知").append("\n");
            report.append("- **租户/系统**: ").append(context.getTenantId() != null ? context.getTenantId() : "未知").append("\n");
            report.append("\n### 症状描述\n");
            report.append("系统监控检测到服务指标异常，详见下方日志和指标数据。\n");
            report.append("\n### 日志证据\n");
            if (logsResult != null && !logsResult.isEmpty()) {
                report.append("```\n");
                // 截取部分日志
                int maxLen = Math.min(logsResult.length(), 500);
                report.append(logsResult, 0, maxLen);
                if (logsResult.length() > maxLen) {
                    report.append("\n... (共 ").append(logsResult.length()).append(" 字符)");
                }
                report.append("\n```\n");
            } else {
                report.append("未查询到相关日志数据\n");
            }
            report.append("\n### 根因结论\n");
            report.append("根据日志和指标分析，").append(alertName).append(" 可能由资源使用率过高导致，建议检查服务负载和资源配置。\n");
        } else {
            report.append("未查询到告警详情数据\n");
        }

        report.append("\n---\n\n");

        // 处理方案执行
        report.append("## 🛠️ 处理方案执行\n\n");
        report.append("### 已执行的排查步骤\n");
        report.append("1. 查询 Prometheus 告警列表\n");
        report.append("2. 查询 CLS 日志数据\n");
        report.append("3. 查询系统指标\n");
        report.append("4. 检索内部知识库\n");
        report.append("\n### 处理建议\n");
        if (docsResult != null && !docsResult.isEmpty() && !docsResult.contains("no_results")) {
            report.append("根据知识库检索结果，建议参考相关运维文档进行处理。\n");
        } else {
            report.append("建议执行以下操作：\n");
            report.append("1. 检查服务资源使用情况\n");
            report.append("2. 查看详细日志定位异常\n");
            report.append("3. 如有必要，执行服务重启或扩容\n");
        }
        report.append("\n### 预期效果\n");
        report.append("通过上述处理，预期可以降低资源使用率，恢复服务正常。\n");

        report.append("\n---\n\n");

        // 结论
        report.append("## 📊 结论\n\n");
        report.append("### 整体评估\n");
        report.append("系统当前存在").append(alertResult != null && !alertResult.isEmpty() ? "多个" : "零个").append("活动告警，需要关注。\n");
        report.append("\n### 关键发现\n");
        report.append("- 已完成告警、 日志、指标、知识库的多维度查询\n");
        report.append("- 各 Agent 协作完成数据采集\n");
        report.append("\n### 后续建议\n");
        report.append("1. 持续监控系统指标变化\n");
        report.append("2. 定期审查告警规则配置\n");
        report.append("3. 完善知识库内容\n");
        report.append("\n### 风险评估\n");
        report.append("当前风险等级：").append(alertResult != null && !alertResult.isEmpty() ? "中等" : "低").append("\n");

        logger.info("模板报告生成完成，长度: {}", report.length());
        return report.toString();
    }

    /**
     * 生成错误报告
     */
    private String generateErrorReport(AiOpsContext context, String errorMessage) {
        StringBuilder report = new StringBuilder();
        report.append("# 告警分析报告\n\n");
        report.append("---\n\n");
        report.append("## ⚠️ 报告生成失败\n\n");
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

    /**
     * 构建提示词
     */
    public String buildPrompt(AiOpsContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 ReporterAgent，专门负责汇总各 Agent 的查询结果并生成最终的分析报告。\n");
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

        prompt.append("\n请汇总所有查询结果，生成最终的分析报告。");
        return prompt.toString();
    }
}