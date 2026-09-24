package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.enrichment.QueryEnricher;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.intent.IntentClassification;
import com.bharat.scholarship_rag_backend.intent.IntentResponse;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemoryManager;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryManager;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryResponse;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatOrchestrator {

    private final OpenAiStreamingChatModel openAiStreamingChatModel;
    private final ConversationMemory conversationMemory;
    private final ConversationMemoryManager conversationMemoryManager;
    private final QueryGate queryGate;
    private final QueryEnricher queryEnricher;
    private final IntentClassification intentClassification;
    private final SemanticMemoryManager semanticMemoryManager;
    private final QueryEmbedding queryEmbedding;
    private final RetrievalQueryReformulator retrievalQueryReformulator;

    public ChatOrchestrator(
            OpenAiStreamingChatModel openAiStreamingChatModel,
            ConversationMemory conversationMemory,
            ConversationMemoryManager conversationMemoryManager,
            QueryGate queryGate,
            QueryEnricher queryEnricher,
            IntentClassification intentClassification,
            SemanticMemoryManager semanticMemoryManager,
            QueryEmbedding queryEmbedding,
            RetrievalQueryReformulator retrievalQueryReformulator
    ) {
        this.openAiStreamingChatModel = openAiStreamingChatModel;
        this.conversationMemory = conversationMemory;
        this.conversationMemoryManager = conversationMemoryManager;
        this.queryGate = queryGate;
        this.queryEnricher = queryEnricher;
        this.intentClassification = intentClassification;
        this.semanticMemoryManager = semanticMemoryManager;
        this.queryEmbedding = queryEmbedding;
        this.retrievalQueryReformulator = retrievalQueryReformulator;
    }

    public ChatResponse processChat(ChatRequest chatRequest) {

        String conversationId = chatRequest.getConversationId();

        // Fetch recent conversation memory
        List<ChatMessage> recentConversationMessages =
                conversationMemory.allRecentConversation(conversationId);

        // Clean user query
        String cleanedQuery = queryGate.clean(chatRequest.getQuery());

        // Enrich query using recent conversation context when required
        String enrichedQuery = queryGate.needsContext(cleanedQuery)
                ? queryEnricher.enrich(cleanedQuery, recentConversationMessages)
                : cleanedQuery;

        log.info("Processing query {}", enrichedQuery);

        // Intent classification
        IntentResponse intentResponse =
                intentClassification.classify(enrichedQuery);

        // Response will be created by the corresponding intent flow
        ChatResponse chatResponse = null;

        switch (intentResponse.getType()) {

            case SCHOLARSHIP -> {

                // Add current user message to conversation memory
                conversationMemoryManager.addUserMessage(chatRequest);

                // Generate query embedding
                float[] queryEmbeddings = queryEmbedding.embed(enrichedQuery);

                // Retrieve relevant semantic memories
                List<SemanticMemoryResponse> semanticMemories =
                        semanticMemoryManager.listAllSemanticMemories(
                                conversationId,
                                queryEmbeddings
                        );

                // Reformulate Query through enrichedQuery and semanticMemories
                String retrievalQuery =
                        retrievalQueryReformulator.
                                reformulate(enrichedQuery, semanticMemories);

                System.out.println("Reformulate Query through enrichedQuery and semanticMemories "+retrievalQuery);

                // enrichedQuery and semanticMemories flow into the retrieval/answer
                // phase which runs after this branch.
                chatResponse = new ChatResponse();
                chatResponse.setConversationMemorySummary(null);
                chatResponse.setSemanticMemorySummary(null);
            }

            case GENERAL_CHAT -> {

                // Add current user message to conversation memory
                conversationMemoryManager.addUserMessage(chatRequest);

                // Generate general conversation response
                String response = streamAnswer(
                        enrichedQuery,
                        recentConversationMessages
                );

                chatResponse = new ChatResponse();
                chatResponse.setResponse(response);
                chatResponse.setConversationMemorySummary(null);
                chatResponse.setSemanticMemorySummary(null);
            }

            case UNKNOWN -> {

                chatResponse = new ChatResponse();

                chatResponse.setResponse(
                        "I'm sorry, I couldn't understand your question. "
                                + "Please ask about scholarships, eligibility, deadlines, "
                                + "applications, or financial aid."
                );
                chatResponse.setConversationMemorySummary(null);
                chatResponse.setSemanticMemorySummary(null);
            }

            default -> {

                chatResponse = new ChatResponse();

                chatResponse.setResponse(
                        "I'm sorry, I couldn't process your request. Please try again."
                );
                chatResponse.setConversationMemorySummary(null);
                chatResponse.setSemanticMemorySummary(null);
            }
        }

        return chatResponse;
    }


    private String streamAnswer(String query, List<ChatMessage> recentConversationMessages) {
        StringBuilder answer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        String prompt = buildPrompt(query, recentConversationMessages);

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

    private String buildPrompt(String query, List<ChatMessage> recentConversationMessages) {
        String history = recentConversationMessages.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .collect(Collectors.joining("\n"));

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful scholarship assistant. Answer using the conversation history.\n\n");

        if (!history.isBlank()) {
            prompt.append("Conversation history:\n").append(history).append("\n\n");
        }

        prompt.append("User query: ").append(query);
        return prompt.toString();
    }
}