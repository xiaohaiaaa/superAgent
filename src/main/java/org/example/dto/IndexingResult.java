package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 索引结果 DTO
 */
@Setter
@Getter
@Schema(description = "索引操作结果")
public class IndexingResult {

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "索引目录路径")
    private String directoryPath;

    @Schema(description = "总文件数")
    private int totalFiles;

    @Schema(description = "成功索引的文件数")
    private int successCount;

    @Schema(description = "失败的文件数")
    private int failCount;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "失败文件及错误原因映射")
    private Map<String, String> failedFiles = new HashMap<>();

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public void incrementFailCount() {
        this.failCount++;
    }

    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }

    public void addFailedFile(String filePath, String error) {
        this.failedFiles.put(filePath, error);
    }
}