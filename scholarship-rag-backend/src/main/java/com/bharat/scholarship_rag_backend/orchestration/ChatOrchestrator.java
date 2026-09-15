package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.memory.MemoryManager;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatOrchestrator {

    private final OpenAiStreamingChatModel openAiStreamingChatModel;
    private final ConversationMemory conversationMemory;
    private final MemoryManager memoryManager;
    private final QueryGate queryGate;

    public ChatOrchestrator(OpenAiStreamingChatModel openAiStreamingChatModel,ConversationMemory conversationMemory,MemoryManager memoryManager,QueryGate queryGate) {
        this.openAiStreamingChatModel = openAiStreamingChatModel;
        this.conversationMemory = conversationMemory;
        this.memoryManager = memoryManager;
        this.queryGate = queryGate;
    }

    public ChatResponse processChat(ChatRequest chatRequest) {
        StringBuilder answer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        //Fetch Recent Conversation memories
        List<ChatMessage> recentConversationMessages = conversationMemory
                .allRecentConversation(chatRequest.getConversationId());

        String cleanedQuery = queryGate.clean(chatRequest.getQuery());

        boolean needsContext = queryGate.needsContext(cleanedQuery);

        openAiStreamingChatModel.chat(cleanedQuery, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                answer.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("LLM streaming timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }

        return new ChatResponse();
    }
}