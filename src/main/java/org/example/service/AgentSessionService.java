package org.example.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agent 会话服务接口
 */
public interface AgentSessionService {

    /**
     * 获取或创建会话上下文
     */
    Object getOrCreateSession(String sessionId);

    /**
     * 获取会话的锁，用于串行化同一 session 的并发请求
     */
    ReentrantLock getSessionLock(String sessionId);

    /**
     * 获取会话的 Agent 实例
     */
    Object getAgent(String sessionId);

    /**
     * 更新会话的系统提示词
     */
    void updateSessionPrompt(String sessionId, List<Map<String, String>> history, String question);

    /**
     * 重新创建会话的 Agent 实例
     */
    void recreateAgent(String sessionId, List<Map<String, String>> history, String question);

    /**
     * 清除会话上下文
     */
    void clearSession(String sessionId);

    /**
     * 获取当前活跃的会话数量
     */
    int getActiveSessionCount();
}