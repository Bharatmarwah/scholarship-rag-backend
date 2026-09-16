package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.enrichment.QueryEnricher;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.intent.IntentClassification;
import com.bharat.scholarship_rag_backend.intent.IntentResponse;
import com.bharat.scholarship_rag_backend.intent.IntentType;
import com.bharat.scholarship_rag_backend.memory.MemoryManager;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ChatOrchestrator {

    private final OpenAiStreamingChatModel openAiStreamingChatModel;
    private final ConversationMemory conversationMemory;
    private final MemoryManager memoryManager;
    private final QueryGate queryGate;
    private final QueryEnricher queryEnricher;
    private final IntentClassification intentClassification;

    public ChatOrchestrator(
            OpenAiStreamingChatModel openAiStreamingChatModel,
            ConversationMemory conversationMemory,
            MemoryManager memoryManager,
            QueryGate queryGate,
            QueryEnricher queryEnricher,
            IntentClassification intentClassification
    ) {
        this.openAiStreamingChatModel = openAiStreamingChatModel;
        this.conversationMemory = conversationMemory;
        this.memoryManager = memoryManager;
        this.queryGate = queryGate;
        this.queryEnricher = queryEnricher;
        this.intentClassification=intentClassification;
    }

    public ChatResponse processChat(ChatRequest chatRequest) {
        String conversationId = chatRequest.getConversationId();

        //fetch all the recent conversation memory
        List<ChatMessage> recentConversationMessages = conversationMemory
                .allRecentConversation(conversationId);

        //enriched query
        String cleanedQuery = queryGate.clean(chatRequest.getQuery());
        String enrichedQuery = queryGate.needsContext(cleanedQuery)
                ? queryEnricher.enrich(cleanedQuery, recentConversationMessages)
                : cleanedQuery;

        //Intent classification
        IntentResponse intentResponse = intentClassification
                .classify(enrichedQuery);

        if(intentResponse.getType()!=IntentType.UNKNOWN){
            memoryManager.addUserMessage(chatRequest);
            //Memory Retrieve
        }

        List<TextSegment> context = List.of();

        String answer = streamAnswer(enrichedQuery, recentConversationMessages, context);

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setConversationSummary(answer);

//        memoryManager.addUserMessage(chatRequest);
//        memoryManager.addAssistantMessage(conversationId, chatResponse);

        return chatResponse;
    }

    private String streamAnswer(String query, List<ChatMessage> recentConversationMessages, List<TextSegment> context) {
        StringBuilder answer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        String prompt = buildPrompt(query, recentConversationMessages, context);

        openAiStreamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
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

        return answer.toString();
    }

    private String buildPrompt(String query, List<ChatMessage> recentConversationMessages, List<TextSegment> context) {
        String history = recentConversationMessages.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .collect(Collectors.joining("\n"));

        String retrieved = context.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n\n"));

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful scholarship assistant. Answer using the provided context and conversation history.\n\n");

        if (!history.isBlank()) {
            prompt.append("Conversation history:\n").append(history).append("\n\n");
        }
        if (!retrieved.isBlank()) {
            prompt.append("Context:\n").append(retrieved).append("\n\n");
        }

        prompt.append("User query: ").append(query);
        return prompt.toString();
    }
}