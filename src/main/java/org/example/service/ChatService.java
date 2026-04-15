package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 聊天服务接口
 * 定义 ReactAgent 对话的核心业务方法
 */
public interface ChatService {

    /**
     * 创建 DashScope API 实例
     * @return DashScopeApi 实例
     */
    DashScopeApi createDashScopeApi();

    /**
     * 创建 ChatModel
     * @param dashScopeApi DashScope API 实例
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     * @return DashScopeChatModel 实例
     */
    DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP);

    /**
     * 创建标准对话 ChatModel（默认参数）
     * @param dashScopeApi DashScope API 实例
     * @return DashScopeChatModel 实例
     */
    DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi);

    /**
     * 创建 AI Ops 专用的 ChatModel（低温度、高 Token）
     * @return DashScopeChatModel 实例
     */
    DashScopeChatModel createAiOpsChatModel();

    /**
     * 构建增强的系统提示词（包含前置检索的上下文）
     * 使用 HyRAG（Hybrid RAG）模式：在调用大模型前先检索相关文档，将结果直接注入提示词
     * 提示词模板从配置文件加载，支持动态修改
     *
     * @param history 历史消息列表
     * @param question 当前用户问题
     * @return 包含RAG上下文的系统提示词
     */
    String buildSystemPromptWithRAGContext(List<Map<String, String>> history, String question);

    /**
     * 构建不含RAG上下文的系统提示词（用于初始化会话等不需要检索的场景）
     *
     * @param history 历史消息列表
     * @return 不含RAG上下文的系统提示词
     */
    String buildSystemPromptWithoutRAG(List<Map<String, String>> history);

    /**
     * 获取工具回调列表，mcp服务提供的工具
     * @return ToolCallback 数组
     */
    ToolCallback[] getToolCallbacks();

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt);

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     * @throws Exception 如果对话执行失败
     */
    String executeChat(ReactAgent agent, String question) throws Exception;

    /**
     * 流式执行 ReactAgent 对话
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @param onChunk 内容块回调（每个 token）
     * @param onComplete 完成回调（传入完整答案）
     * @param onError 错误回调
     */
    void executeStreamChat(ReactAgent agent, String question,
                           Consumer<String> onChunk, Consumer<String> onComplete, Consumer<Throwable> onError);

    /**
     * 根据会话历史和问题构建 ReactAgent
     * @param history 历史消息列表
     * @param question 当前问题
     * @return 配置好的 ReactAgent
     */
    ReactAgent buildReactAgent(List<Map<String, String>> history, String question);

    /**
     * 执行普通对话（完整流程）
     * @param history 历史消息列表
     * @param question 当前问题
     * @return AI 回复
     * @throws Exception 如果对话执行失败
     */
    String chat(List<Map<String, String>> history, String question) throws Exception;
}