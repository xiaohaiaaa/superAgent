package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 简化告警信息 DTO
 */
@Setter
@Getter
@Schema(description = "简化告警信息")
public class SimplifiedAlert {

    @JsonProperty("alert_name")
    @Schema(description = "告警名称")
    private String alertName;

    @JsonProperty("description")
    @Schema(description = "告警描述")
    private String description;

    @JsonProperty("state")
    @Schema(description = "告警状态")
    private String state;

    @JsonProperty("active_at")
    @Schema(description = "激活时间")
    private String activeAt;

    @JsonProperty("duration")
    @Schema(description = "持续时间")
    private String duration;
}