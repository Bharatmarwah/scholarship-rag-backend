package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.composer.QueryComposer;
import com.bharat.scholarship_rag_backend.composer.QueryComposerResult;
import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityAnswerFormatter;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityEngine;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityReport;
import com.bharat.scholarship_rag_backend.enrichment.QueryEnricher;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.intent.IntentClassification;
import com.bharat.scholarship_rag_backend.intent.IntentResponse;
import com.bharat.scholarship_rag_backend.intent.IntentType;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemoryManager;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryManager;
import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.retrieval.ReformulatedQuery;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipAnswerGenerator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipRetrieval;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.service.StudentProfileService;
import com.bharat.scholarship_rag_backend.validator.PendingAnswerResolver;
import com.bharat.scholarship_rag_backend.validator.PendingValidationState;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import com.bharat.scholarship_rag_backend.validator.ValidationMode;
import com.bharat.scholarship_rag_backend.validator.ValidationResult;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the READY scholarship path: once validation passes, the grounded
 * answer produced by {@link ScholarshipAnswerGenerator} must reach the caller.
 * A missing assignment there returned {@code null}, which surfaced as an empty
 * 200 response.
 */
class ChatOrchestratorScholarshipResponseTest {

    private final OpenAiStreamingChatModel streamingModel = mock(OpenAiStreamingChatModel.class);
    private final ConversationMemory conversationMemory = mock(ConversationMemory.class);
    private final ConversationMemoryManager conversationMemoryManager = mock(ConversationMemoryManager.class);
    private final QueryGate queryGate = mock(QueryGate.class);
    private final QueryEnricher queryEnricher = mock(QueryEnricher.class);
    private final IntentClassification intentClassification = mock(IntentClassification.class);
    private final SemanticMemoryManager semanticMemoryManager = mock(SemanticMemoryManager.class);
    private final QueryEmbedding queryEmbedding = mock(QueryEmbedding.class);
    private final QueryComposer queryComposer = mock(QueryComposer.class);
    private final RetrievalQueryReformulator retrievalQueryReformulator = mock(RetrievalQueryReformulator.class);
    private final StudentProfileService studentProfileService = mock(StudentProfileService.class);
    private final ProfileValueNormalizer profileValueNormalizer = mock(ProfileValueNormalizer.class);
    private final PendingValidationState pendingValidationState = mock(PendingValidationState.class);
    private final PendingAnswerResolver pendingAnswerResolver = mock(PendingAnswerResolver.class);
    private final ValidationEngine validationEngine = mock(ValidationEngine.class);
    private final ScholarshipRetrieval scholarshipRetrieval = mock(ScholarshipRetrieval.class);
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator = mock(ScholarshipAnswerGenerator.class);
    private final EligibilityEngine eligibilityEngine = mock(EligibilityEngine.class);
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter = mock(EligibilityAnswerFormatter.class);

    private final ChatOrchestrator orchestrator = new ChatOrchestrator(
            streamingModel,
            conversationMemory,
            conversationMemoryManager,
            queryGate,
            queryEnricher,
            intentClassification,
            semanticMemoryManager,
            queryEmbedding,
            queryComposer,
            retrievalQueryReformulator,
            studentProfileService,
            profileValueNormalizer,
            pendingValidationState,
            pendingAnswerResolver,
            validationEngine,
            scholarshipRetrieval,
            scholarshipAnswerGenerator,
            eligibilityEngine,
            eligibilityAnswerFormatter);

    @Test
    void readyScholarshipAnswerIsReturnedToCaller() {
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn("what is the income limit");
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.SCHOLARSHIP));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[768]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult("combined", List.of(), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);
        when(validationEngine.validateAll(any(), anyList(), anyString()))
                .thenReturn(ValidationResult.ready(ValidationMode.SPECIFIC_SCHEME, null, List.of()));
        when(retrievalQueryReformulator.reformulate(anyString(), anyList(), any(), any()))
                .thenReturn(ReformulatedQuery.unscoped("combined"));
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());

        ChatResponse generated = new ChatResponse("The income limit is eight lakh.", "semantic", "conversation");
        when(scholarshipAnswerGenerator.answer(anyString(), anyList())).thenReturn(generated);

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery("what is the income limit");

        ChatResponse result = orchestrator.processChat(request);

        assertNotNull(result, "orchestrator must not return null on a ready scholarship answer");
        assertEquals("The income limit is eight lakh.", result.getResponse());
    }

    @Test
    void readyWithNamedSchemeUsesEligibilityInsteadOfRetrieval() {
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn("i am an sc student in my first year");
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.SCHOLARSHIP));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[768]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult("combined", List.of("Top Class SC"), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);
        when(validationEngine.validateAll(any(), anyList(), anyString()))
                .thenReturn(ValidationResult.ready(
                        ValidationMode.SPECIFIC_SCHEME,
                        SchemeType.TOP_CLASS_SC,
                        List.of(SchemeType.TOP_CLASS_SC)));

        EligibilityReport report = new EligibilityReport(List.of());
        when(eligibilityEngine.assessAll(any(), anyList())).thenReturn(report);
        when(eligibilityAnswerFormatter.format(any(), any()))
                .thenReturn("You appear to meet the eligibility conditions for Top Class SC.");

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery("i am an sc student in my first year");

        ChatResponse result = orchestrator.processChat(request);

        assertEquals("You appear to meet the eligibility conditions for Top Class SC.",
                result.getResponse());
        verify(eligibilityEngine).assessAll(profile, List.of(SchemeType.TOP_CLASS_SC));
        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
    }
}
