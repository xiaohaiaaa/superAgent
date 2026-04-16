package org.example.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.apache.logging.log4j.util.Strings;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.service.ChatService;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 聊天服务实现类
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)
    private QueryLogsTools queryLogsTools;

    @Autowired
    private ToolCallbackProvider tools;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${prompts.prerag}")
    private String preragPromptTemplate;

    @Value("${prompts.history}")
    private String historyTemplate;

    @Value("${prompts.ending}")
    private String endingTemplate;

    @Override
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    @Override
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

    @Override
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    @Override
    public DashScopeChatModel createAiOpsChatModel() {
        DashScopeApi dashScopeApi = createDashScopeApi();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(0.3)
                        .withMaxToken(8000)
                        .withTopP(0.9)
                        .build())
                .build();
    }

    @Override
    public String buildSystemPromptWithRAGContext(List<Map<String, String>> history, String question) {
        StringBuilder promptBuilder = new StringBuilder();

        String ragContext = "暂无相关文档。\n";;
        List<org.example.dto.SearchResult> searchResults = new ArrayList<>();
        try {
            if (Strings.isNotBlank(question)) {
                searchResults = vectorSearchService.searchSimilarDocuments(question, 5);
            }

            if (!searchResults.isEmpty()) {
                StringBuilder contextBuilder = new StringBuilder();
                for (int i = 0; i < searchResults.size(); i++) {
                    org.example.dto.SearchResult result = searchResults.get(i);
                    contextBuilder.append(String.format("[文档 %d] (相似度: %.2f)\n%s\n\n", i + 1, 1.0f - result.getScore(), result.getContent()));
                }
                ragContext = contextBuilder.toString();
            }
        } catch (Exception e) {
            logger.warn("前置RAG检索失败: {}", e.getMessage());
            ragContext = "文档检索失败，请基于你的知识回答。\n";
        }

        String prompt = preragPromptTemplate.replace("{rag_context}", ragContext);
        promptBuilder.append(prompt);

        if (!history.isEmpty()) {
            promptBuilder.append("\n");
            String historyContent = formatHistory(history);
            promptBuilder.append(historyTemplate.replace("{history}", historyContent));
        }

        promptBuilder.append("\n").append(endingTemplate);
        return promptBuilder.toString();
    }

    private String formatHistory(List<Map<String, String>> history) {
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("user".equals(role)) {
                sb.append("用户: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                sb.append("助手: ").append(content).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return tools.getToolCallbacks();
    }

    private Object[] buildMethodToolsArray() {
        List<Object> toolsList = new ArrayList<>();
        toolsList.add(dateTimeTools);
        toolsList.add(internalDocsTools);
        toolsList.add(queryMetricsTools);
        if (queryLogsTools != null) {
            toolsList.add(queryLogsTools);
        }
        return toolsList.toArray();
    }

    @Override
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    @Override
    public String executeChat(ReactAgent agent, String question) throws Exception {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }

    @Override
    public void executeStreamChat(ReactAgent agent, String question,
                                  Consumer<String> onChunk, Consumer<String> onComplete, Consumer<Throwable> onError) {
        StringBuilder fullAnswerBuilder = new StringBuilder();

        Flux<NodeOutput> stream;
        try {
            stream = agent.stream(question);
        } catch (Exception e) {
            onError.accept(e);
            return;
        }
        stream.subscribe(
            output -> {
                try {
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();

                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            String chunk = streamingOutput.message().getText();
                            if (chunk != null && !chunk.isEmpty()) {
                                fullAnswerBuilder.append(chunk);
                                onChunk.accept(chunk);
                            }
                        } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                            logger.info("工具调用完成: {}", output.node());
                        }
                    }
                } catch (Exception e) {
                    onError.accept(e);
                }
            },
            onError,
            () -> onComplete.accept(fullAnswerBuilder.toString())
        );
    }

    @Override
    public ReactAgent buildReactAgent(List<Map<String, String>> history, String question) {
        String systemPrompt = buildSystemPromptWithRAGContext(history, question);
        DashScopeApi dashScopeApi = createDashScopeApi();
        DashScopeChatModel chatModel = createStandardChatModel(dashScopeApi);
        return createReactAgent(chatModel, systemPrompt);
    }

    @Override
    public String chat(List<Map<String, String>> history, String question) throws Exception {
        ReactAgent agent = buildReactAgent(history, question);
        return executeChat(agent, question);
    }
}