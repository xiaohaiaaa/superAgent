package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件上传响应 DTO
 */
@Setter
@Getter
@Schema(description = "文件上传响应")
public class FileUploadRes {

    @Schema(description = "文件名", example = "document.pdf")
    private String fileName;

    @Schema(description = "文件路径", example = "/uploads/document.pdf")
    private String filePath;

    @Schema(description = "文件大小（字节）", example = "1024000")
    private Long fileSize;

    public FileUploadRes() {
    }

    public FileUploadRes(String fileName, String filePath, Long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }
}