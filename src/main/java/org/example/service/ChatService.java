package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)  // Mock 模式下才注册，所以设置为 optional,真实环境通过mcp配置注入
    private QueryLogsTools queryLogsTools;

    @Autowired
    private ToolCallbackProvider tools;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    // 是否启用前置检索模式（HyRAG）- 在调用大模型前先检索相关文档
    @Value("${chat.enable-prerag:true}")
    private boolean enablePreRAG = true;

    // 前置检索模式下是否保留 RAG 工具作为备选
    // true: 保留 RAG 工具，模型可在已注入文档基础上进一步检索
    // false: 移除 RAG 工具，完全依赖前置检索的文档
    @Value("${chat.prerag-keep-rag-tool:false}")
    private boolean preRAGKeepRagTool = false;

    /**
     * 创建 DashScope API 实例
     */
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    /**
     * 构建系统提示词（包含历史消息）
     * @param history 历史消息列表
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(List<Map<String, String>> history) {
        StringBuilder systemPromptBuilder = new StringBuilder();

        // 基础系统提示
        systemPromptBuilder.append("你是一个专业的智能助手，可以获取当前时间、查询天气信息、搜索内部文档知识库，以及查询 Prometheus 告警信息。\n");
        systemPromptBuilder.append("【重要】在回答任何问题之前，请优先使用 queryInternalDocs 工具检索内部知识库，基于检索到的文档内容回答用户问题。\n");
        systemPromptBuilder.append("当用户询问时间相关问题时，使用 getCurrentDateTime 工具。\n");
        systemPromptBuilder.append("当用户需要查询公司内部文档、流程、最佳实践或技术指南时，使用 queryInternalDocs 工具。\n");
        systemPromptBuilder.append("当用户需要查询 Prometheus 告警、监控指标或系统告警状态时，使用 queryPrometheusAlerts 工具。\n");
        systemPromptBuilder.append("当用户需要查询腾讯云日志时，请调用腾讯云mcp服务查询,默认查询地域ap-guangzhou,查询时间范围为近一个月。\n\n");

        // 添加历史消息
        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }

        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");

        return systemPromptBuilder.toString();
    }

    /**
     * 构建增强的系统提示词（包含前置检索的上下文）
     * 使用 HyRAG（Hybrid RAG）模式：在调用大模型前先检索相关文档，将结果直接注入提示词
     *
     * @param history 历史消息列表
     * @param question 当前用户问题
     * @return 包含RAG上下文的系统提示词
     */
    public String buildSystemPromptWithRAGContext(List<Map<String, String>> history, String question) {
        StringBuilder systemPromptBuilder = new StringBuilder();

        // 基础系统提示
        systemPromptBuilder.append("你是一个专业的智能助手，可以获取当前时间、查询天气信息、搜索内部文档知识库，以及查询 Prometheus 告警信息。\n");
        systemPromptBuilder.append("当用户询问时间相关问题时，使用 getCurrentDateTime 工具。\n");
        systemPromptBuilder.append("当用户需要查询 Prometheus 告警、监控指标或系统告警状态时，使用 queryPrometheusAlerts 工具。\n");
        systemPromptBuilder.append("当用户需要查询腾讯云日志时，请调用腾讯云mcp服务查询,默认查询地域ap-guangzhou,查询时间范围为近一个月。\n\n");

        // 前置检索：先查询相关文档
        systemPromptBuilder.append("--- 内部文档知识库（已为您检索）---\n");
        try {
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(question, 5);

            if (searchResults.isEmpty()) {
                systemPromptBuilder.append("未在知识库中找到相关文档。\n");
            } else {
                for (int i = 0; i < searchResults.size(); i++) {
                    VectorSearchService.SearchResult result = searchResults.get(i);
                    systemPromptBuilder.append(String.format("[文档 %d] (相似度: %.2f)\n%s\n\n",
                            i + 1, 1.0f - result.getScore(), result.getContent()));
                }
            }
        } catch (Exception e) {
            logger.warn("前置RAG检索失败: {}", e.getMessage());
            systemPromptBuilder.append("文档检索失败，请基于你的知识回答。\n");
        }
        systemPromptBuilder.append("--- 知识库内容结束 ---\n\n");

        systemPromptBuilder.append("【重要】请优先参考上述检索到的文档内容回答用户问题。");
        if (preRAGKeepRagTool) {
            systemPromptBuilder.append("如果文档中有相关信息，请基于文档内容作答；如果文档中没有相关信息，可以使用 queryInternalDocs 工具进一步检索，或基于你的通用知识回答。\n\n");
        } else {
            systemPromptBuilder.append("文档内容已包含在上下文中，请基于这些信息回答问题。如果文档中没有相关信息，请基于你的通用知识回答。\n\n");
        }

        // 添加历史消息
        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }

        systemPromptBuilder.append("请基于以上检索到的文档内容和对话历史，回答用户的新问题。");

        return systemPromptBuilder.toString();
    }

    /**
     * 判断是否使用前置检索模式
     */
    public boolean isPreRAGEnabled() {
        return enablePreRAG;
    }

    /**
     * 设置是否使用前置检索模式
     */
    public void setPreRAGEnabled(boolean enabled) {
        this.enablePreRAG = enabled;
    }

    /**
     * 判断前置检索模式下是否保留 RAG 工具
     */
    public boolean isPreRAGKeepRagTool() {
        return preRAGKeepRagTool;
    }

    /**
     * 设置前置检索模式下是否保留 RAG 工具
     */
    public void setPreRAGKeepRagTool(boolean keepRagTool) {
        this.preRAGKeepRagTool = keepRagTool;
    }

    /**
     * 动态构建方法工具数组
     * 根据 cls.mock-enabled 决定是否包含 QueryLogsTools
     * 根据 includeRagTool 决定是否包含 InternalDocsTools（前置检索模式下可选）
     *
     * @param includeRagTool 是否包含 RAG 工具
     * @return 工具数组
     */
    public Object[] buildMethodToolsArray(boolean includeRagTool) {
        List<Object> toolsList = new ArrayList<>();

        // 始终包含时间工具
        toolsList.add(dateTimeTools);

        // 根据参数决定是否包含 RAG 工具
        if (includeRagTool) {
            toolsList.add(internalDocsTools);
        }

        // 始终包含查询指标工具
        toolsList.add(queryMetricsTools);

        // Mock 模式下包含日志查询工具
        if (queryLogsTools != null) {
            toolsList.add(queryLogsTools);
        }

        return toolsList.toArray();
    }

    /**
     * 动态构建方法工具数组（默认包含 RAG 工具）
     * 根据 cls.mock-enabled 决定是否包含 QueryLogsTools
     */
    public Object[] buildMethodToolsArray() {
        return buildMethodToolsArray(true);
    }

    /**
     * 获取工具回调列表，mcp服务提供的工具
     */
    public ToolCallback[] getToolCallbacks() {
        return tools.getToolCallbacks();
    }

    /**
     * 记录可用工具列表：mcp服务提供的工具
     */
    public void logAvailableTools() {
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        logger.info("可用工具列表:");
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @param includeRagTool 是否包含 RAG 工具（前置检索模式下可设为 false 避免重复检索）
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt, boolean includeRagTool) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray(includeRagTool))
                .tools(getToolCallbacks())
                .build();
    }

    /**
     * 创建 ReactAgent（默认包含 RAG 工具）
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return createReactAgent(chatModel, systemPrompt, true);
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
