package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryResponse;
import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.enrichment.QueryEnricher;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.intent.IntentClassification;
import com.bharat.scholarship_rag_backend.intent.IntentResponse;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemoryManager;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryManager;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemorySelector;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
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
    private final ConversationMemoryManager conversationMemoryManager;
    private final QueryGate queryGate;
    private final QueryEnricher queryEnricher;
    private final IntentClassification intentClassification;
    private final SemanticMemoryManager semanticMemoryManager;
    private final QueryEmbedding queryEmbedding;
    private final RetrievalQueryReformulator retrievalQueryReformulator;
    private final SemanticMemorySelector semanticMemorySelector;

    public ChatOrchestrator(
            OpenAiStreamingChatModel openAiStreamingChatModel,
            ConversationMemory conversationMemory,
            ConversationMemoryManager conversationMemoryManager,
            QueryGate queryGate,
            QueryEnricher queryEnricher,
            IntentClassification intentClassification,
            SemanticMemoryManager semanticMemoryManager,
            QueryEmbedding queryEmbedding,
            RetrievalQueryReformulator retrievalQueryReformulator,
            SemanticMemorySelector semanticMemorySelector
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
        this.semanticMemorySelector = semanticMemorySelector;
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

                //reRanking
                semanticMemories =
                        semanticMemorySelector
                                .select(semanticMemories);


                // Reformulate Query through enrichedQuery and semanticMemories
                String retrievalQuery =
                        retrievalQueryReformulator.
                                reformulate(enrichedQuery, semanticMemories);


                // TODO: Retrieve relevant scholarship chunks from pgvector

                // TODO: Rerank/filter retrieved scholarship chunks

                // TODO: Build final LLM context

                // TODO: Generate final scholarship answer

                // TODO: Add assistant response to conversation memory

                // TODO: Extract and save new semantic memory if required
                return new ChatResponse();
            }

            case GENERAL_CHAT -> {

                // Add current user message to conversation memory
                conversationMemoryManager.addUserMessage(chatRequest);

                // Generate general conversation response
                String response = streamAnswer(
                        enrichedQuery,
                        recentConversationMessages,
                        List.of()
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


    private String streamAnswer(String query, List<ChatMessage> recentConversationMessages, List<SemanticMemoryResponse> context) {
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

    private String buildPrompt(String query, List<ChatMessage> recentConversationMessages, List<SemanticMemoryResponse> context) {
        String history = recentConversationMessages.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .collect(Collectors.joining("\n"));

        String retrieved = context.stream()
                .map(SemanticMemoryResponse::getContext)
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