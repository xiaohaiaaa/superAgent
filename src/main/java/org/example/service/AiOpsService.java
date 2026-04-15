package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.ai.tool.ToolCallback;

import java.util.Optional;

/**
 * AI Ops 服务接口
 */
public interface AiOpsService {

    /**
     * 执行 AI Ops 告警分析流程
     *
     * @param chatModel 大模型实例
     * @param toolCallbacks 工具回调数组
     * @return 分析结果状态
     */
    Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) throws Exception;

    /**
     * 从执行结果中提取最终报告文本
     *
     * @param state 执行状态
     * @return 报告文本（如果存在）
     */
    Optional<String> extractFinalReport(OverAllState state);
}