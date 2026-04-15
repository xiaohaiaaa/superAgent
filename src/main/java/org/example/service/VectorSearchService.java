package org.example.service;

import org.example.dto.SearchResult;

import java.util.List;

/**
 * 向量搜索服务接口
 */
public interface VectorSearchService {

    /**
     * 搜索相似文档
     *
     * @param query 查询文本
     * @param topK 返回最相似的K个结果
     * @return 搜索结果列表
     */
    List<SearchResult> searchSimilarDocuments(String query, int topK);
}