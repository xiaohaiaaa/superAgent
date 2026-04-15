package org.example.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.dto.*;
import org.example.service.AgentSessionService;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.example.service.RedisSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 统一 API 控制器
 */
@RestController
@RequestMapping("/api")
@Tag(name = "聊天API", description = "对话服务接口")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ToolCallbackProvider tools;

    @Autowired
    private RedisSessionService redisSessionService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Operation(summary = "普通对话", description = "支持工具调用的对话接口，非流式返回")
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        String sessionId = resolveSessionId(request.getId());

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
        }

        ReentrantLock lock = agentSessionService.getSessionLock(sessionId);
        lock.lock();
        try {
            var history = redisSessionService.getHistory(sessionId);
            String answer = chatService.chat(history, request.getQuestion());
            redisSessionService.addMessage(sessionId, request.getQuestion(), answer);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(answer)));
        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        } finally {
            lock.unlock();
        }
    }

    @Operation(summary = "清空会话历史", description = "清除指定会话的所有历史消息")
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request) {
        try {
            if (request.getId() == null || request.getId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("会话ID不能为空"));
            }

            if (redisSessionService.exists(request.getId())) {
                redisSessionService.clearHistory(request.getId());
                agentSessionService.clearSession(request.getId());
                return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }
        } catch (Exception e) {
            logger.error("清空会话历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "流式对话", description = "SSE流式对话接口，支持多轮对话和自动工具调用")
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);
        String sessionId = resolveSessionId(request.getId());

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("message").data(SseMessage.error("问题内容为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        executor.execute(() -> {
            ReentrantLock lock = agentSessionService.getSessionLock(sessionId);
            lock.lock();
            try {
                var history = redisSessionService.getHistory(sessionId);

                chatService.executeStreamChat(
                    chatService.buildReactAgent(history, request.getQuestion()),
                    request.getQuestion(),
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event().name("message").data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    fullAnswer -> {
                        redisSessionService.addMessage(sessionId, request.getQuestion(), fullAnswer);
                        try {
                            emitter.send(SseEmitter.event().name("message").data(SseMessage.done(), MediaType.APPLICATION_JSON));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        logger.error("流式对话失败", error);
                        try {
                            emitter.send(SseEmitter.event().name("message").data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
                        } catch (IOException ex) {
                            logger.error("发送错误消息失败", ex);
                        }
                        emitter.completeWithError(error);
                    }
                );
            } catch (Exception e) {
                logger.error("对话初始化失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message").data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            } finally {
                lock.unlock();
            }
        });

        return emitter;
    }

    @Operation(summary = "AI智能运维", description = "自动分析告警并生成运维报告，无需用户输入")
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        SseEmitter emitter = new SseEmitter(600000L);

        executor.execute(() -> {
            try {
                DashScopeChatModel chatModel = chatService.createAiOpsChatModel();
                ToolCallback[] toolCallbacks = tools.getToolCallbacks();

                sendSseMessage(emitter, "正在读取告警并拆解任务...\n");

                Optional<OverAllState> overAllStateOptional = aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);
                if (overAllStateOptional.isEmpty()) {
                    sendSseMessage(emitter, SseMessage.error("多 Agent 编排未获取到有效结果"));
                    emitter.complete();
                    return;
                }

                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(overAllStateOptional.get());
                if (finalReportOptional.isPresent()) {
                    sendReport(emitter, finalReportOptional.get());
                } else {
                    sendSseMessage(emitter, "⚠️ 多 Agent 流程已完成，但未能生成最终报告。");
                }

                sendSseMessage(emitter, SseMessage.done());
                emitter.complete();
            } catch (Exception e) {
                logger.error("AI Ops 多 Agent 协作失败", e);
                sendSseMessage(emitter, SseMessage.error("AI Ops 流程失败: " + e.getMessage()));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Operation(summary = "获取会话信息", description = "查询指定会话的基本信息")
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            if (redisSessionService.exists(sessionId)) {
                SessionInfoResponse response = new SessionInfoResponse();
                response.setSessionId(sessionId);
                response.setMessagePairCount(redisSessionService.getMessagePairCount(sessionId));
                response.setCreateTime(0L);
                return ResponseEntity.ok(ApiResponse.success(response));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }
        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isEmpty()) ? UUID.randomUUID().toString() : sessionId;
    }

    private void sendReport(SseEmitter emitter, String reportText) throws IOException {
        sendSseMessage(emitter, "\n\n" + "=".repeat(60) + "\n");
        sendSseMessage(emitter, "📋 **告警分析报告**\n\n");

        for (int i = 0; i < reportText.length(); i += 50) {
            sendSseMessage(emitter, reportText.substring(i, Math.min(i + 50, reportText.length())));
        }

        sendSseMessage(emitter, "\n" + "=".repeat(60) + "\n\n");
    }

    private void sendSseMessage(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().name("message").data(SseMessage.content(content), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendSseMessage(SseEmitter emitter, SseMessage message) {
        try {
            emitter.send(SseEmitter.event().name("message").data(message, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}