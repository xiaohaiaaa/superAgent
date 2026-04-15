package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档分片 DTO
 */
@Setter
@Getter
@Schema(description = "文档分片")
public class DocumentChunk {

    @Schema(description = "分片内容")
    private String content;

    @Schema(description = "分片在原文档中的起始位置", example = "0")
    private int startIndex;

    @Schema(description = "分片在原文档中的结束位置", example = "500")
    private int endIndex;

    @Schema(description = "分片序号（从0开始）", example = "0")
    private int chunkIndex;

    @Schema(description = "分片标题或上下文信息")
    private String title;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }
}