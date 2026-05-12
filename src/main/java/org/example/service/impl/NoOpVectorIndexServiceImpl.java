package org.example.service.impl;

import org.example.dto.IndexingResult;
import org.example.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 向量索引服务 NOOP 实现
 * 当 milvus.enabled=false 时使用，不执行实际索引
 */
@Service
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "false")
public class NoOpVectorIndexServiceImpl implements VectorIndexService {

    private static final Logger logger = LoggerFactory.getLogger(NoOpVectorIndexServiceImpl.class);

    public NoOpVectorIndexServiceImpl() {
        logger.warn("NoOpVectorIndexServiceImpl 初始化，Milvus 已禁用，向量索引功能不可用");
    }

    @Override
    public IndexingResult indexDirectory(String directoryPath) {
        logger.warn("向量索引功能已禁用，请启用 Milvus 以使用此功能");
        IndexingResult result = new IndexingResult();
        result.setSuccess(false);
        result.setErrorMessage("Milvus 已禁用，向量索引功能不可用");
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        return result;
    }

    @Override
    public void indexSingleFile(String filePath) throws Exception {
        logger.warn("向量索引功能已禁用，请启用 Milvus 以使用此功能");
        throw new UnsupportedOperationException("Milvus 已禁用，向量索引功能不可用");
    }
}
