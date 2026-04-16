package org.example.service.impl;

import org.example.config.KryoConfig.KryoFactory;
import org.example.dto.SessionMessage;
import org.example.service.RedisSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 会话服务实现
 */
@Service
public class RedisSessionServiceImpl implements RedisSessionService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionServiceImpl.class);

    private static final String KEY_PREFIX = "session:";
    private static final String KEY_SUFFIX = ":history";

    private final StringRedisTemplate redisTemplate;
    private final KryoFactory kryoFactory;

    @Value("${session.ttl-seconds:86400}")
    private long ttlSeconds;

    @Value("${session.max-window-size:6}")
    private int maxWindowSize;

    public RedisSessionServiceImpl(StringRedisTemplate redisTemplate, KryoFactory kryoFactory) {
        this.redisTemplate = redisTemplate;
        this.kryoFactory = kryoFactory;
    }

    @Override
    public void addMessage(String sessionId, String userQuestion, String aiAnswer) {
        String key = buildKey(sessionId);
        try {
            long now = System.nanoTime();

            // 1. 先确保 TTL 存在（key 不存在时返回 false，不影响后续操作）
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);

            String userData = kryoFactory.serializeToString(new SessionMessage("user", userQuestion, now));
            String assistantData = kryoFactory.serializeToString(new SessionMessage("assistant", aiAnswer, now));

            ZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();

            // 2. 添加消息到 ZSET
            zsetOps.add(key, userData, now);
            zsetOps.add(key, assistantData, now + 1); // assistant 消息稍晚一点

            // 3. 维护滑动窗口：移除超过限制的旧消息
            Long size = zsetOps.size(key);
            if (size != null && size > maxWindowSize * 2) {
                // 按分数从小到大排序，移除最老的 (size - maxWindowSize * 2) 条
                long removeCount = size - maxWindowSize * 2;
                zsetOps.removeRange(key, 0, removeCount - 1);
            }

            // 4. 再设置一次过期时间，防止只交流了一次的对话没有设置TTL
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);

            logger.debug("Session {} 历史消息更新完成，当前消息数: {}", sessionId, size);
        } catch (Exception e) {
            throw new RuntimeException("序列化消息失败", e);
        }
    }

    @Override
    public List<Map<String, String>> getHistory(String sessionId) {
        String key = buildKey(sessionId);
        ZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();

        // 按分数从小到大排序，获取所有消息
        Set<String> rawMessages = zsetOps.range(key, 0, -1);
        if (rawMessages == null || rawMessages.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, String>> history = new ArrayList<>(rawMessages.size());
        for (String raw : rawMessages) {
            try {
                SessionMessage msg = kryoFactory.deserializeFromString(raw);
                Map<String, String> messageMap = new LinkedHashMap<>();
                messageMap.put("role", msg.getRole());
                messageMap.put("content", msg.getContent());
                history.add(messageMap);
            } catch (Exception e) {
                logger.warn("反序列化历史消息失败，跳过该条: {}", raw, e);
            }
        }
        return history;
    }

    @Override
    public void clearHistory(String sessionId) {
        String key = buildKey(sessionId);
        Boolean deleted = redisTemplate.delete(key);
        logger.info("Session {} 历史已清空，key 存在: {}", sessionId, deleted);
    }

    @Override
    public int getMessagePairCount(String sessionId) {
        String key = buildKey(sessionId);
        Long size = redisTemplate.opsForZSet().size(key);
        return size == null ? 0 : (int) (size / 2);
    }

    @Override
    public boolean exists(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(sessionId)));
    }

    @Override
    public void refreshTtl(String sessionId) {
        redisTemplate.expire(buildKey(sessionId), ttlSeconds, TimeUnit.SECONDS);
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId + KEY_SUFFIX;
    }
}