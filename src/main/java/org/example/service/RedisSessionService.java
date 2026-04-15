package org.example.service;

import java.util.List;
import java.util.Map;

/**
 * Redis 会话服务接口
 */
public interface RedisSessionService {

    /**
     * 添加一对消息（用户问题 + AI 回复），并维护滑动窗口
     */
    void addMessage(String sessionId, String userQuestion, String aiAnswer);

    /**
     * 获取会话历史消息列表（按时间正序返回）
     */
    List<Map<String, String>> getHistory(String sessionId);

    /**
     * 清空会话历史
     */
    void clearHistory(String sessionId);

    /**
     * 获取当前消息对数
     */
    int getMessagePairCount(String sessionId);

    /**
     * 判断 session 是否存在
     */
    boolean exists(String sessionId);

    /**
     * 刷新 session TTL
     */
    void refreshTtl(String sessionId);
}