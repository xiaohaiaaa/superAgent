package org.example.service;

import org.example.dto.IndexingResult;

/**
 * 向量索引服务接口
 */
public interface VectorIndexService {

    /**
     * 索引指定目录下的所有文件
     *
     * @param directoryPath 目录路径（可选，默认使用配置的上传目录）
     * @return 索引结果
     */
    IndexingResult indexDirectory(String directoryPath);

    /**
     * 索引单个文件
     *
     * @param filePath 文件路径
     * @throws Exception 索引失败时抛出异常
     */
    void indexSingleFile(String filePath) throws Exception;
}