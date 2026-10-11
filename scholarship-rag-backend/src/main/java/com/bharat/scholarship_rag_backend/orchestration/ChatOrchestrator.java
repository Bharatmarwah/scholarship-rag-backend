package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.composer.QueryComposer;
import com.bharat.scholarship_rag_backend.composer.QueryComposerResult;
import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.enrichment.QueryEnricher;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.intent.IntentClassification;
import com.bharat.scholarship_rag_backend.intent.IntentResponse;
import com.bharat.scholarship_rag_backend.intent.IntentType;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemoryManager;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryManager;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryRequest;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryResponse;
import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.retrieval.ReformulatedQuery;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipAnswerGenerator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipChunkMatch;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipRetrieval;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import com.bharat.scholarship_rag_backend.service.StudentProfileService;
import com.bharat.scholarship_rag_backend.validator.PendingAnswerResolver;
import com.bharat.scholarship_rag_backend.validator.PendingValidationState;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import com.bharat.scholarship_rag_backend.validator.ValidationResult;
import com.bharat.scholarship_rag_backend.validator.ValidationStatus;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final QueryComposer queryComposer;
    private final StudentProfileService studentProfileService;
    private final ProfileValueNormalizer profileValueNormalizer;
    private final PendingValidationState pendingValidationState;
    private final PendingAnswerResolver pendingAnswerResolver;
    private final RetrievalQueryReformulator retrievalQueryReformulator;
    private final ValidationEngine validationEngine;
    private final ScholarshipRetrieval scholarshipRetrieval;
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator;

    public ChatOrchestrator(
            OpenAiStreamingChatModel openAiStreamingChatModel,
            ConversationMemory conversationMemory,
            ConversationMemoryManager conversationMemoryManager,
            QueryGate queryGate,
            QueryEnricher queryEnricher,
            IntentClassification intentClassification,
            SemanticMemoryManager semanticMemoryManager,
            QueryEmbedding queryEmbedding,
            QueryComposer queryComposer,
            RetrievalQueryReformulator retrievalQueryReformulator,
            StudentProfileService studentProfileService,
            ProfileValueNormalizer profileValueNormalizer,
            PendingValidationState pendingValidationState,
            PendingAnswerResolver pendingAnswerResolver,
            ValidationEngine validationEngine,
            ScholarshipRetrieval scholarshipRetrieval,
            ScholarshipAnswerGenerator scholarshipAnswerGenerator
    ) {
        this.openAiStreamingChatModel = openAiStreamingChatModel;
        this.conversationMemory = conversationMemory;
        this.conversationMemoryManager = conversationMemoryManager;
        this.queryGate = queryGate;
        this.queryEnricher = queryEnricher;
        this.intentClassification = intentClassification;
        this.semanticMemoryManager = semanticMemoryManager;
        this.queryEmbedding = queryEmbedding;
        this.queryComposer = queryComposer;
        this.retrievalQueryReformulator = retrievalQueryReformulator;
        this.studentProfileService = studentProfileService;
        this.profileValueNormalizer = profileValueNormalizer;
        this.pendingValidationState = pendingValidationState;
        this.pendingAnswerResolver = pendingAnswerResolver;
        this.validationEngine = validationEngine;
        this.scholarshipRetrieval = scholarshipRetrieval;
        this.scholarshipAnswerGenerator = scholarshipAnswerGenerator;
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

        log.info("Processed query {}", enrichedQuery);

        // Intent classification. History is passed so a reply that continues an
        // ongoing scholarship/eligibility exchange (e.g. answering a profile
        // question) is recognised as SCHOLARSHIP instead of falling to UNKNOWN.
        IntentResponse intentResponse =
                intentClassification.classify(
                        enrichedQuery,
                        recentConversationMessages);

        // Deterministic fallback: after the assistant asked a question in the
        // previous turn (for example a validation question), the next user
        // message is very likely an answer to it. Route it through the
        // scholarship flow so the composer can extract the profile values.
        if (intentResponse.getType() == IntentType.UNKNOWN
                && isAnswerToAssistantsQuestion(recentConversationMessages)) {
            log.info("Routing UNKNOWN query to SCHOLARSHIP flow: previous assistant turn was a question");
            intentResponse = IntentResponse.forType(IntentType.SCHOLARSHIP);
        }

        log.info("Intent type {}",intentResponse.getType());

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

                // Compose a self-contained query from the latest query (STM),
                // conversation history (STM) and semantic memories (LTM), and
                // extract the target scheme plus any stated profile values.
                QueryComposerResult composerResult =
                        queryComposer.compose(
                                enrichedQuery,
                                recentConversationMessages,
                                semanticMemories
                        ); 

                log.info("Composed query {} target schemes {}",
                        composerResult.getCombinedQuery(),
                        composerResult.getTargetSchemes());

                //Get or create student profile
                StudentProfileDto profile =
                        studentProfileService
                                .getOrCreate(chatRequest.getConversationId());

                //Apply the profile values extracted by the composer, correcting
                //existing fields only when the extracted value differs. Rule-based
                //fallbacks backfill a category the LLM may have missed from the
                //student's own words, so the field never gets stuck re-asking.
                profile = studentProfileService.applyExtracted(
                        chatRequest.getConversationId(),
                        withDeterministicFallbacks(
                                chatRequest.getConversationId(),
                                cleanedQuery,
                                composerResult)
                );

                // Validate the profile against the target scheme (or discovery
                // mode when no scheme was named). Ask for missing information;
                // only when every required field is present do we proceed to
                // retrieval/answer. When the student's own turn is an
                // informational question ("what is a social category?") the
                // questions are suppressed and the query is answered instead.
                boolean asQuestion = isInformationalQuestion(cleanedQuery);
                ValidationResult validationResult =
                        validationEngine.validateAll(
                                profile,
                                composerResult.getTargetSchemes(),
                                chatRequest.getConversationId(),
                                asQuestion
                        );

                log.info("Validation status {} mode {} target schemes {}",
                        validationResult.getStatus(),
                        validationResult.getMode(),
                        validationResult.getTargetSchemes());

                if (validationResult.getStatus() == ValidationStatus.MISSING_INFORMATION) {
                    String validationQuestion = validationResult.getQuestion();

                    chatResponse = new ChatResponse();
                    chatResponse.setResponse(validationQuestion);
                    chatResponse.setConversationMemorySummary(validationQuestion);
                    chatResponse.setSemanticMemorySummary(null);

                    conversationMemoryManager.addAssistantMessage(
                            conversationId,
                            chatResponse
                    );
                } else {
                    // Reformulate Query through combinedQuery, semanticMemories....
                    // and student profile to return the best possible
                    // scholarship chunks. The resolved scheme travels with the
                    // query so retrieval can filter on it.
                    ReformulatedQuery reformulatedQuery =
                            retrievalQueryReformulator.
                                    reformulate(
                                            composerResult.getCombinedQuery(),
                                            semanticMemories,
                                            profile,
                                            validationResult
                                    );

                    log.info("Reformulated query {} for schemes {}",
                            reformulatedQuery.query(),
                            reformulatedQuery.schemeNames());

                    // Search the knowledge base. The resolved scheme list is a
                    // hard filter so another scheme's rules can never be
                    // presented as this scheme's answer.
                    List<ScholarshipChunkMatch> matches =
                            scholarshipRetrieval.search(
                                    reformulatedQuery.query(),
                                    reformulatedQuery.candidates());

                    log.info("Retrieved {} chunks: {}", matches.size(),
                            matches.stream().map(ScholarshipChunkMatch::citation).toList());

                    // Answer strictly from the retrieved chunks.
                    ChatResponse response = scholarshipAnswerGenerator.answer(
                            enrichedQuery,
                            matches);

                    // Save semantic memory for this exchange
                    try {
                        semanticMemoryManager.addSemanticMemory(
                                new SemanticMemoryRequest(
                                        conversationId,
                                        enrichedQuery,
                                        queryEmbeddings,
                                        response.getResponse(),
                                        queryEmbeddings
                                )
                        );
                    } catch (Exception e) {
                        log.warn("Failed to save semantic memory for conversation {}", conversationId, e);
                    }

                    conversationMemoryManager.addAssistantMessage(
                            conversationId,
                            response
                    );
                }
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

    static boolean isAnswerToAssistantsQuestion(List<ChatMessage> recentConversationMessages) {
        if (recentConversationMessages == null || recentConversationMessages.isEmpty()) {
            return false;
        }
        ChatMessage last = recentConversationMessages.get(recentConversationMessages.size() - 1);
        return last.getRole() == MessageRole.ASSISTANT
                && last.getContent() != null
                && last.getContent().trim().endsWith("?");
    }

    /** Tokens that mark the student's turn as a knowledge question, not an answer. */
    private static final java.util.Set<String> QUESTION_TOKENS = java.util.Set.of(
            "what", "why", "how", "which", "when", "who", "where", "whose",
            "explain", "define", "definition", "meaning");

    /**
     * True when the student is asking for information rather than answering a
     * question. Such a turn must be answered from the knowledge base, not turned
     * into another profile question.
     */
    static boolean isInformationalQuestion(String cleanedQuery) {
        if (cleanedQuery == null || cleanedQuery.isBlank()) {
            return false;
        }
        String query = cleanedQuery.toLowerCase(java.util.Locale.ROOT).trim();
        if (query.endsWith("?")) {
            return true;
        }
        if (query.startsWith("tell me") || query.contains("tell me about")) {
            return true;
        }
        String normalized = query.replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isEmpty()) {
            return false;
        }
        for (String token : normalized.split(" +")) {
            if (QUESTION_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hybrid safety net over the LLM's extracted values. Two deterministic
     * layers run: first a per-field detector for values the model missed
     * entirely, then a pending-answer resolver that maps a plain reply onto the
     * field the assistant just asked about. Only absent fields are filled; the
     * model's output is never overridden.
     */
    private Map<String, Object> withDeterministicFallbacks(
            String conversationId,
            String userQuery,
            QueryComposerResult composerResult) {

        Map<String, Object> merged = new LinkedHashMap<>();
        if (composerResult.getExtractedValues() != null) {
            merged.putAll(composerResult.getExtractedValues());
        }
        if (isAbsent(merged.get("SOCIAL_CATEGORY"))) {
            profileValueNormalizer.detectSocialCategory(userQuery)
                    .ifPresent(category -> merged.put("SOCIAL_CATEGORY", category));
        }
        if (isAbsent(merged.get("COURSE"))) {
            profileValueNormalizer.detectCourse(userQuery)
                    .ifPresent(course -> merged.put("COURSE", course));
        }

        // When no new scheme was named this turn, the student is likely
        // answering the question just asked. Resolve the reply onto the asked
        // field so a valid answer is never dropped and re-asked.
        boolean continuingExchange = composerResult.getTargetSchemes() == null
                || composerResult.getTargetSchemes().isEmpty();
        if (continuingExchange) {
            List<StudentProfileField> asked = pendingValidationState.askedFields(conversationId);
            if (!asked.isEmpty()) {
                pendingAnswerResolver.resolve(asked, userQuery, merged.keySet())
                        .forEach(merged::putIfAbsent);
            }
        }
        return merged;
    }

    private static boolean isAbsent(Object value) {
        return value == null || (value instanceof CharSequence text && text.toString().isBlank());
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