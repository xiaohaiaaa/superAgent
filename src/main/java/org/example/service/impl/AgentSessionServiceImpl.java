package org.example.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.example.service.AgentSessionService;
import org.example.service.ChatService;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agent 会话服务实现
 */
@Service
public class AgentSessionServiceImpl implements AgentSessionService {

    private static final Logger logger = LoggerFactory.getLogger(AgentSessionServiceImpl.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    /**
     * 会话上下文：包含 Agent 实例和对应的锁
     */
    private static class SessionContext {
        final ReactAgent agent;
        final DashScopeChatModel chatModel;
        final DashScopeApi dashScopeApi;
        final ReentrantLock lock;

        SessionContext(ReactAgent agent, DashScopeChatModel chatModel, DashScopeApi dashScopeApi) {
            this.agent = agent;
            this.chatModel = chatModel;
            this.dashScopeApi = dashScopeApi;
            this.lock = new ReentrantLock();
        }
    }

    /**
     * 会话上下文缓存
     */
    private final Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();

    @Override
    public Object getOrCreateSession(String sessionId) {
        return sessionContexts.computeIfAbsent(sessionId, this::createSessionContext);
    }

    private SessionContext createSessionContext(String sessionId) {
        logger.info("为 session {} 创建新的 Agent 实例", sessionId);

        DashScopeApi dashScopeApi = chatService.createDashScopeApi();
        DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

        String systemPrompt = chatService.buildSystemPromptWithRAGContext(Collections.emptyList(), "");

        ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

        return new SessionContext(agent, chatModel, dashScopeApi);
    }

    @Override
    public ReentrantLock getSessionLock(String sessionId) {
        SessionContext context = (SessionContext) getOrCreateSession(sessionId);
        return context.lock;
    }

    @Override
    public Object getAgent(String sessionId) {
        SessionContext context = (SessionContext) getOrCreateSession(sessionId);
        return context.agent;
    }

    @Override
    public void updateSessionPrompt(String sessionId, List<Map<String, String>> history, String question) {
        SessionContext context = (SessionContext) getOrCreateSession(sessionId);
        String systemPrompt = chatService.buildSystemPromptWithRAGContext(history, question);
        logger.debug("更新 session {} 的系统提示词", sessionId);
    }

    @Override
    public void recreateAgent(String sessionId, List<Map<String, String>> history, String question) {
        sessionContexts.computeIfPresent(sessionId, (key, oldContext) -> {
            logger.info("重新创建 session {} 的 Agent 实例", sessionId);
            return createSessionContext(key);
        });
    }

    @Override
    public void clearSession(String sessionId) {
        SessionContext removed = sessionContexts.remove(sessionId);
        if (removed != null) {
            logger.info("已清除 session {} 的上下文", sessionId);
        }
    }

    @Override
    public int getActiveSessionCount() {
        return sessionContexts.size();
    }
}