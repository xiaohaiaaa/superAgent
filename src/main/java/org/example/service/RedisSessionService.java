package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式 Session 服务
 * <p>
 * 数据结构：Redis List
 * Key 格式：session:{sessionId}:history
 * 每个元素：JSON 字符串，格式为 {"role":"user","content":"..."}
 * <p>
 * 线程安全：依赖 Redis 单命令原子性，通过 Lua 脚本保证 RPUSH+LTRIM+EXPIRE 的原子性
 */
@Service
public class RedisSessionService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionService.class);

    private static final String KEY_PREFIX = "session:";
    private static final String KEY_SUFFIX = ":history";

    /**
     * Lua 脚本：原子地执行 RPUSH(user) + RPUSH(assistant) + LTRIM + EXPIRE
     * KEYS[1] = history key
     * ARGV[1] = user message JSON
     * ARGV[2] = assistant message JSON
     * ARGV[3] = max list size (MAX_WINDOW_SIZE * 2)
     * ARGV[4] = TTL seconds
     */
    private static final String ADD_MESSAGE_SCRIPT =
            "redis.call('RPUSH', KEYS[1], ARGV[1], ARGV[2])\n" +
            "local len = redis.call('LLEN', KEYS[1])\n" +
            "local maxSize = tonumber(ARGV[3])\n" +
            "if len > maxSize then\n" +
            "  redis.call('LTRIM', KEYS[1], len - maxSize, -1)\n" +
            "end\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[4])\n" +
            "return redis.call('LLEN', KEYS[1])";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${session.ttl-seconds:86400}")
    private long ttlSeconds;

    @Value("${session.max-window-size:6}")
    private int maxWindowSize;

    public RedisSessionService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 添加一对消息（用户问题 + AI 回复），并维护滑动窗口
     * 使用 Lua 脚本保证原子性，天然线程安全
     */
    public void addMessage(String sessionId, String userQuestion, String aiAnswer) {
        String key = buildKey(sessionId);
        try {
            String userJson = objectMapper.writeValueAsString(Map.of("role", "user", "content", userQuestion));
            String assistantJson = objectMapper.writeValueAsString(Map.of("role", "assistant", "content", aiAnswer));

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(ADD_MESSAGE_SCRIPT, Long.class);
            Long newLen = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    userJson,
                    assistantJson,
                    String.valueOf(maxWindowSize * 2),
                    String.valueOf(ttlSeconds)
            );
            logger.debug("Session {} 历史消息更新完成，当前消息数: {}", sessionId, newLen);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化消息失败", e);
        }
    }

    /**
     * 获取会话历史消息列表（线程安全，返回副本）
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        String key = buildKey(sessionId);
        List<String> rawList = redisTemplate.opsForList().range(key, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, String>> history = new ArrayList<>(rawList.size());
        for (String raw : rawList) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> msg = objectMapper.readValue(raw, Map.class);
                history.add(msg);
            } catch (JsonProcessingException e) {
                logger.warn("反序列化历史消息失败，跳过该条: {}", raw, e);
            }
        }
        return history;
    }

    /**
     * 清空会话历史
     */
    public void clearHistory(String sessionId) {
        String key = buildKey(sessionId);
        Boolean deleted = redisTemplate.delete(key);
        logger.info("Session {} 历史已清空，key 存在: {}", sessionId, deleted);
    }

    /**
     * 获取当前消息对数
     */
    public int getMessagePairCount(String sessionId) {
        String key = buildKey(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : (int) (size / 2);
    }

    /**
     * 判断 session 是否存在（即 key 是否存在于 Redis）
     */
    public boolean exists(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(sessionId)));
    }

    /**
     * 刷新 session TTL（用户有新活动时可选调用）
     */
    public void refreshTtl(String sessionId) {
        redisTemplate.expire(buildKey(sessionId), ttlSeconds, TimeUnit.SECONDS);
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId + KEY_SUFFIX;
    }
}
