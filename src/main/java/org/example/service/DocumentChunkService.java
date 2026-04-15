package org.example.service;

import org.example.dto.DocumentChunk;

import java.util.List;

/**
 * 文档分片服务接口
 */
public interface DocumentChunkService {

    /**
     * 智能分片文档
     *
     * @param content 文档内容
     * @param filePath 文件路径
     * @return 文档分片列表
     */
    List<DocumentChunk> chunkDocument(String content, String filePath);

    /**
     * 使用 TokenTextSplitter 进行文档分片
     *
     * @param content 文档内容
     * @param filePath 文件路径
     * @return 文档分片列表
     */
    List<DocumentChunk> chunkDocumentV2(String content, String filePath);
}