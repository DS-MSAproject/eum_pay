package com.eum.rag.chat.controller;

import com.eum.rag.chat.dto.request.ChatRequest;
import com.eum.rag.chat.dto.response.ChatResponse;
import com.eum.rag.chat.dto.response.SessionHistoryResponse;
import com.eum.rag.chat.service.ChatService;
import com.eum.rag.chat.service.ChatSessionService;
import com.eum.rag.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;

    // 메인 채팅 엔드포인트. sessionId가 비어 있으면 서버가 새 세션을 생성해 반환한다.
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.chat(request));
    }

    // 채팅 UI 복원을 위한 세션 이력 조회 엔드포인트.
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionHistoryResponse> getSessionHistory(@PathVariable String sessionId) {
        return ApiResponse.success(chatSessionService.getHistory(sessionId));
    }
}
