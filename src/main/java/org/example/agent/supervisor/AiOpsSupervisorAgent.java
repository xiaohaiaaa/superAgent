package org.example.agent.supervisor;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.agent.AlertAgent;
import org.example.agent.DocsAgent;
import org.example.agent.LogsAgent;
import org.example.agent.MetricsAgent;
import org.example.agent.ReporterAgent;
import org.example.dto.AiOpsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AiOpsSupervisorAgent - AI Ops 总调度 Agent
 *
 * 职责：
 * - 接收原始告警请求
 * - 并行分发任务给 4 个专业 Agent（Alert/Logs/Metrics/Docs）
 * - 收集汇总结果
 * - 调用 ReporterAgent 生成最终报告
 * - 增加最大迭代次数保护（防止无限循环）
 */
@Component
public class AiOpsSupervisorAgent {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsSupervisorAgent.class);

    public static final String AGENT_NAME = "ai_ops_supervisor";
    public static final int MAX_ITERATIONS = 10;
    public static final long AGENT_TIMEOUT_SECONDS = 30;

    @Autowired
    private AlertAgent alertAgent;

    @Autowired
    private LogsAgent logsAgent;

    @Autowired
    private MetricsAgent metricsAgent;

    @Autowired
    private DocsAgent docsAgent;

    @Autowired
    private ReporterAgent reporterAgent;

    /**
     * Agent 执行结果容器
     */
    private static class AgentExecutionResult {
        String result;
        boolean failed;
        String errorMessage;

        AgentExecutionResult(String result, boolean failed, String errorMessage) {
            this.result = result;
            this.failed = failed;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 执行多 Agent 协作流程
     *
     * @param chatModel 大模型实例
     * @param context 多租户上下文
     * @param toolCallbacks 工具回调数组
     * @return 最终报告
     */
    public String execute(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks) {
        logger.info("AiOpsSupervisorAgent 开始执行，tenantId: {}, serviceId: {}, alertName: {}",
                context.getTenantId(), context.getServiceId(), context.getAlertName());

        long startTime = System.currentTimeMillis();

        try {
            // 执行多 Agent 协作（带依赖感知）
            executeWithDependency(chatModel, context, toolCallbacks);

            // 调用 ReporterAgent 生成最终报告
            logger.info("调用 ReporterAgent 生成最终报告");
            String report = reporterAgent.generateReport(chatModel, context);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("AiOpsSupervisorAgent 执行完成，耗时: {}ms", duration);

            return report;

        } catch (Exception e) {
            logger.error("AiOpsSupervisorAgent 执行失败", e);
            return reporterAgent.generateReport(chatModel, context);
        }
    }

    /**
     * 带依赖感知的多 Agent 执行
     *
     * 执行策略：
     * Phase 1: 先执行 AlertAgent 获取告警信息
     * Phase 2: 根据告警类型决定后续查询策略（智能路由）
     * Phase 3: 并行执行 LogsAgent, MetricsAgent, DocsAgent
     */
    private void executeWithDependency(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks) {
        int iteration = 0;

        while (iteration < MAX_ITERATIONS) {
            iteration++;
            logger.info("开始第 {} 次迭代", iteration);

            // Phase 1: 先执行 AlertAgent（告警是入口）
            logger.info("Phase 1: 执行 AlertAgent");
            AgentExecutionResult alertResult = executeAlertAgent(chatModel, context, toolCallbacks);

            if (alertResult.failed || alertResult.result == null || alertResult.result.isEmpty()) {
                logger.warn("AlertAgent 执行失败，无法继续");
                context.putSharedData("alert_result", "{\"status\":\"error\",\"message\":\"告警查询失败\"}");
                break;
            }

            // 解析告警结果，提取告警名称和服务
            parseAlertAndEnrichContext(context, alertResult.result);

            // Phase 2: 根据告警类型，决定查询策略
            logger.info("Phase 2: 分析告警类型，决定查询策略");
            QueryStrategy strategy = decideQueryStrategy(context);
            context.putSharedData("query_strategy", strategy);
            logger.info("查询策略: {}", strategy);

            // Phase 3: 并行执行其他 Agent
            logger.info("Phase 3: 并行执行 LogsAgent, MetricsAgent, DocsAgent");
            AgentExecutionResult[] results = executeAgentsInParallel(chatModel, context, toolCallbacks, strategy);

            // 记录各 Agent 执行状态
            logAgentResults(results);

            // 检查是否满足终止条件
            TerminationReason reason = shouldTerminate(results, iteration);
            if (reason != null) {
                logger.info("满足终止条件: {}, 结束迭代", reason);
                break;
            }

            if (iteration >= MAX_ITERATIONS) {
                logger.warn("达到最大迭代次数 {}，强制终止", MAX_ITERATIONS);
                break;
            }
        }
    }

    /**
     * 解析告警结果并丰富上下文
     */
    private void parseAlertAndEnrichContext(AiOpsContext context, String alertResult) {
        context.putSharedData("alert_result", alertResult);

        // 从告警结果中提取告警名称（简单解析）
        // 实际应该解析 JSON，这里做简化处理
        try {
            if (alertResult.contains("HighCPUUsage")) {
                context.setAlertName("HighCPUUsage");
                context.putSharedData("alert_type", "CPU");
            } else if (alertResult.contains("HighMemoryUsage")) {
                context.setAlertName("HighMemoryUsage");
                context.putSharedData("alert_type", "MEMORY");
            } else if (alertResult.contains("SlowResponse")) {
                context.setAlertName("SlowResponse");
                context.putSharedData("alert_type", "LATENCY");
            } else {
                context.putSharedData("alert_type", "UNKNOWN");
            }
        } catch (Exception e) {
            logger.warn("解析告警结果失败: {}", e.getMessage());
            context.putSharedData("alert_type", "UNKNOWN");
        }
    }

    /**
     * 查询策略枚举
     */
    private enum QueryStrategy {
        ALL,           // 查询所有（日志+指标+文档）
        CPU_FOCUSED,   // CPU 告警：重点查系统指标和进程日志
        MEMORY_FOCUSED,// 内存告警：重点查 JVM 指标和 GC 日志
        LATENCY_FOCUSED, // 延迟告警：重点查慢请求日志和下游依赖日志
        ERROR_FOCUSED  // 错误告警：重点查应用错误日志
    }

    /**
     * 根据告警类型决定查询策略
     */
    private QueryStrategy decideQueryStrategy(AiOpsContext context) {
        String alertType = context.getSharedData("alert_type", "UNKNOWN");
        String alertName = context.getAlertName();

        if (alertName != null) {
            alertName = alertName.toLowerCase();
            if (alertName.contains("cpu")) {
                return QueryStrategy.CPU_FOCUSED;
            } else if (alertName.contains("memory") || alertName.contains("oom")) {
                return QueryStrategy.MEMORY_FOCUSED;
            } else if (alertName.contains("slow") || alertName.contains("latency")) {
                return QueryStrategy.LATENCY_FOCUSED;
            } else if (alertName.contains("error") || alertName.contains("500")) {
                return QueryStrategy.ERROR_FOCUSED;
            }
        }

        return QueryStrategy.ALL;
    }

    /**
     * 执行 AlertAgent（单独执行）
     */
    private AgentExecutionResult executeAlertAgent(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks) {
        try {
            String result = alertAgent.execute(chatModel, context, toolCallbacks);
            return new AgentExecutionResult(result, false, null);
        } catch (Exception e) {
            logger.error("AlertAgent 执行异常", e);
            return new AgentExecutionResult(null, true, e.getMessage());
        }
    }

    /**
     * 并行执行所有专业 Agent（根据策略决定执行哪些）
     * @param strategy 查询策略
     * @return 每个 Agent 的执行结果数组 [logs, metrics, docs]
     */
    private AgentExecutionResult[] executeAgentsInParallel(DashScopeChatModel chatModel, AiOpsContext context, ToolCallback[] toolCallbacks, QueryStrategy strategy) {
        logger.info("并行触发 Agent: LogsAgent, MetricsAgent, DocsAgent, 策略: {}", strategy);

        // 根据策略决定查询参数
        String queryHint = buildQueryHint(strategy);
        context.putSharedData("query_hint", queryHint);

        // 创建并行任务
        CompletableFuture<AgentExecutionResult> logsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String result = logsAgent.execute(chatModel, context, toolCallbacks);
                return new AgentExecutionResult(result, false, null);
            } catch (Exception e) {
                logger.error("LogsAgent 执行异常", e);
                return new AgentExecutionResult(null, true, e.getMessage());
            }
        });

        CompletableFuture<AgentExecutionResult> metricsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String result = metricsAgent.execute(chatModel, context, toolCallbacks);
                return new AgentExecutionResult(result, false, null);
            } catch (Exception e) {
                logger.error("MetricsAgent 执行异常", e);
                return new AgentExecutionResult(null, true, e.getMessage());
            }
        });

        CompletableFuture<AgentExecutionResult> docsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String result = docsAgent.execute(chatModel, context, toolCallbacks);
                return new AgentExecutionResult(result, false, null);
            } catch (Exception e) {
                logger.error("DocsAgent 执行异常", e);
                return new AgentExecutionResult(null, true, e.getMessage());
            }
        });

        // 等待所有任务完成（带超时）
        AgentExecutionResult[] results = new AgentExecutionResult[3];
        try {
            CompletableFuture.allOf(logsFuture, metricsFuture, docsFuture)
                    .get(AGENT_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);

            results[0] = logsFuture.get();
            results[1] = metricsFuture.get();
            results[2] = docsFuture.get();

        } catch (Exception e) {
            logger.error("Agent 并行执行超时或异常", e);
            results[0] = new AgentExecutionResult(null, true, "执行超时: " + e.getMessage());
            results[1] = new AgentExecutionResult(null, true, "执行超时: " + e.getMessage());
            results[2] = new AgentExecutionResult(null, true, "执行超时: " + e.getMessage());
        }

        return results;
    }

    /**
     * 根据策略构建查询提示
     */
    private String buildQueryHint(QueryStrategy strategy) {
        return switch (strategy) {
            case CPU_FOCUSED -> "cpu usage high load";
            case MEMORY_FOCUSED -> "memory oom jvm gc";
            case LATENCY_FOCUSED -> "slow request downstream timeout";
            case ERROR_FOCUSED -> "error exception 500";
            default -> "";
        };
    }

    /**
     * 记录 Agent 执行结果
     */
    private void logAgentResults(AgentExecutionResult[] results) {
        String[] agentNames = {"AlertAgent", "LogsAgent", "MetricsAgent", "DocsAgent"};
        for (int i = 0; i < results.length; i++) {
            AgentExecutionResult r = results[i];
            if (r.failed) {
                logger.info("  - {}: 失败 - {}", agentNames[i], r.errorMessage);
            } else {
                logger.info("  - {}: 成功 ({} 字符)", agentNames[i],
                        r.result != null ? r.result.length() : 0);
            }
        }
    }

    /**
     * 终止原因枚举
     */
    private enum TerminationReason {
        ALL_AGENTS_COMPLETED("所有 Agent 都已完成"),
        ALL_AGENTS_FAILED("所有 Agent 都失败"),
        MAX_ITERATIONS_REACHED("达到最大迭代次数");

        private final String description;
        TerminationReason(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * 判断是否满足终止条件
     *
     * 终止条件（满足任一即终止）：
     * 1. 所有 Agent 都完成了（成功或失败）
     * 2. 所有 Agent 都失败了（无法继续）
     * 3. 达到最大迭代次数
     */
    private TerminationReason shouldTerminate(AgentExecutionResult[] results, int iteration) {
        if (iteration >= MAX_ITERATIONS) {
            return TerminationReason.MAX_ITERATIONS_REACHED;
        }

        int successCount = 0;
        int failureCount = 0;

        for (AgentExecutionResult r : results) {
            if (r.failed) {
                failureCount++;
            } else if (r.result != null && !r.result.isEmpty()
                    && !r.result.contains("\"status\":\"error\"")
                    && !r.result.contains("no_results")) {
                successCount++;
            }
        }

        int totalAgents = results.length;

        // 所有 Agent 都完成了（至少有部分成功）
        if (successCount > 0 && successCount + failureCount == totalAgents) {
            return TerminationReason.ALL_AGENTS_COMPLETED;
        }

        // 所有 Agent 都失败了
        if (failureCount == totalAgents) {
            return TerminationReason.ALL_AGENTS_FAILED;
        }

        // 还有 Agent 在运行中或者还有数据未获取，不终止（进入下一轮迭代）
        return null;
    }

    /**
     * 构建调度提示词
     */
    public String buildSupervisorPrompt(AiOpsContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 AiOpsSupervisorAgent，负责调度多个专业 Agent 完成告警分析任务。\n\n");

        prompt.append("## 可用的专业 Agent\n");
        prompt.append("1. AlertAgent - 负责查询 Prometheus 告警\n");
        prompt.append("2. LogsAgent - 负责查询 CLS 日志\n");
        prompt.append("3. MetricsAgent - 负责查询系统指标\n");
        prompt.append("4. DocsAgent - 负责检索内部知识库\n");
        prompt.append("5. ReporterAgent - 负责生成最终报告\n\n");

        prompt.append("## 当前任务上下文\n");
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

        prompt.append("\n## 执行策略\n");
        prompt.append("1. 并行触发所有专业 Agent 收集数据\n");
        prompt.append("2. 汇总各 Agent 的查询结果\n");
        prompt.append("3. 调用 ReporterAgent 生成最终的分析报告\n");
        prompt.append("4. 最大迭代次数：").append(MAX_ITERATIONS).append("\n");

        return prompt.toString();
    }
}