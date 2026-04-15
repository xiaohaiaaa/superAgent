package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 向量搜索结果 DTO
 */
@Setter
@Getter
@Schema(description = "向量搜索结果")
public class SearchResult {

    @Schema(description = "文档ID")
    private String id;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "相似度得分")
    private float score;

    @Schema(description = "元数据 JSON 字符串")
    private String metadata;
}