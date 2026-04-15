package org.example.service;

import org.example.dto.SearchResult;

import java.util.List;
import java.util.Map;

/**
 * RAG 服务接口
 */
public interface RagService {

    /**
     * 流式处理用户问题（不带历史消息）
     *
     * @param question 用户问题
     * @param callback 流式回调接口
     */
    void queryStream(String question, StreamCallback callback);

    /**
     * 流式处理用户问题（带历史消息）
     *
     * @param question 用户问题
     * @param history 历史消息列表
     * @param callback 流式回调接口
     */
    void queryStream(String question, List<Map<String, String>> history, StreamCallback callback);

    /**
     * 流式回调接口
     */
    interface StreamCallback {
        void onSearchResults(List<SearchResult> results);
        void onReasoningChunk(String chunk);
        void onContentChunk(String chunk);
        void onComplete(String fullContent, String fullReasoning);
        void onError(Exception e);
    }
}