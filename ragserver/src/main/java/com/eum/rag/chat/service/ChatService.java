package com.eum.rag.chat.service;

import com.eum.rag.chat.domain.ChatMessage;
import com.eum.rag.chat.domain.ChatSession;
import com.eum.rag.chat.dto.request.ChatRequest;
import com.eum.rag.chat.dto.response.ChatResponse;
import com.eum.rag.common.config.properties.RagChatProperties;
import com.eum.rag.llm.client.LlmClient;
import com.eum.rag.llm.service.PromptBuilderService;
import com.eum.rag.llm.service.PromptTemplateService;
import com.eum.rag.retrieval.dto.request.HybridSearchRequest;
import com.eum.rag.retrieval.dto.response.HybridSearchResponse;
import com.eum.rag.retrieval.service.HybridSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionService chatSessionService;
    private final QueryRewriteService queryRewriteService;
    private final PromptTemplateService promptTemplateService;
    private final PromptBuilderService promptBuilderService;
    private final LlmClient llmClient;
    private final HybridSearchService hybridSearchService;
    private final RagChatProperties ragChatProperties;
    private final CommerceAnswerService commerceAnswerService;

    public ChatResponse chat(ChatRequest request) {
        // 후속 질문 재작성에 이전 대화가 필요하므로 세션을 먼저 로드(또는 생성)한다.
        ChatSession session = chatSessionService.loadOrCreate(request.sessionId());
        List<ChatMessage> history = session.messages();

        String rewrittenQuestion = rewriteIfNeeded(history, request.question());

        // 상품 추천/상품 정보는 문서 RAG보다 실시간 서비스 데이터(search/product) 우선으로 응답한다.
        var commerceAnswer = commerceAnswerService.tryHandle(rewrittenQuestion);
        if (commerceAnswer.isPresent()) {
            ChatSession updated = chatSessionService.appendTurn(session, request.question(), commerceAnswer.get().answer());
            return new ChatResponse(
                    updated.sessionId(),
                    rewrittenQuestion,
                    commerceAnswer.get().answer(),
                    commerceAnswer.get().sources()
            );
        }

        // 답변 생성 전에 검색을 수행해, 생성 답변이 검색 근거에 기반하도록 강제한다.
        HybridSearchResponse retrieval = hybridSearchService.search(new HybridSearchRequest(rewrittenQuestion, null));
        List<HybridSearchResponse.RetrievedChunk> sources = retrieval.results().stream()
                .limit(ragChatProperties.maxSources())
                .toList();

        String answer;
        if (sources.isEmpty()) {
            answer = "죄송합니다. 제공된 문서에서 해당 질문의 근거를 찾지 못했습니다.";
        } else {
            // 시스템 프롬프트는 문맥 기반 답변을 강제하고, 사용자 프롬프트에는 선택된 근거 청크를 주입한다.
            String userPrompt = promptBuilderService.buildAnswerUserPrompt(sources, rewrittenQuestion);
            answer = llmClient.complete(promptTemplateService.getAnswerSystemPrompt(), userPrompt);
        }

        ChatSession updated = chatSessionService.appendTurn(session, request.question(), answer);

        List<ChatResponse.SourceItem> sourceItems = sources.stream()
                .map(source -> new ChatResponse.SourceItem(
                        source.documentId(),
                        source.filename(),
                        source.chunkId(),
                        abbreviate(source.text(), 300),
                        source.score()
                ))
                .toList();

        return new ChatResponse(updated.sessionId(), rewrittenQuestion, answer, sourceItems);
    }

    private String rewriteIfNeeded(List<ChatMessage> history, String question) {
        if (!queryRewriteService.shouldRewrite(question, history)) {
            return question;
        }

        String rewritePrompt = promptBuilderService.buildRewriteUserPrompt(
                promptTemplateService.getQueryRewritePrompt(),
                history,
                question
        );

        String rewritten = llmClient.complete(
                "당신은 질문 재작성기입니다. 한국어로 standalone 질문 한 문장만 출력하세요.",
                rewritePrompt
        );

        return rewritten == null || rewritten.isBlank() ? question : rewritten.trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
