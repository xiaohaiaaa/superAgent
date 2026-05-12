package org.example.service.impl;

import org.example.dto.SearchResult;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 向量搜索服务 NOOP 实现
 * 当 milvus.enabled=false 时使用，不执行实际搜索
 */
@Service
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "false")
public class NoOpVectorSearchServiceImpl implements VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(NoOpVectorSearchServiceImpl.class);

    public NoOpVectorSearchServiceImpl() {
        logger.warn("NoOpVectorSearchServiceImpl 初始化，Milvus 已禁用，向量搜索功能不可用");
    }

    @Override
    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        logger.warn("向量搜索功能已禁用，请启用 Milvus 以使用此功能");
        return Collections.emptyList();
    }
}
