package com.bharat.scholarship_rag_backend.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.junit.jupiter.api.Test;

class ScholarshipAnswerGeneratorTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private ScholarshipChunkMatch match(ContentType contentType, String content) {
        return new ScholarshipChunkMatch(
                ID, SchemeType.TOP_CLASS_PWD, content, contentType,
                "top-class-pwd/guideline.md", "4. ELIGIBILITY", null,
                3, 3, 1, 400, 0.2, null, 0.1,
                "TOP CLASS PWD, 4. ELIGIBILITY, page 3");
    }

    @Test
    void refusesToAnswerWhenNothingWasRetrieved() {
        ScholarshipAnswerGenerator generator = generator();

        assertEquals(ScholarshipAnswerGenerator.NO_CONTEXT_ANSWER, generator.answer("q", List.of()));
        assertEquals(ScholarshipAnswerGenerator.NO_CONTEXT_ANSWER, generator.answer("q", null));
    }

    @Test
    void rendersPassagesWithProvenanceAndAuthorityLabel() {
        ScholarshipAnswerGenerator generator = generator();

        String rendered = generator.renderPassages(List.of(
                match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh."),
                match(ContentType.FAQ, "Only two siblings may benefit.")));

        assertTrue(rendered.startsWith("[Passage 1] NORMATIVE | TOP CLASS PWD, 4. ELIGIBILITY, page 3"));
        assertTrue(rendered.contains("Income limit is Rs. 8 lakh."));
        assertTrue(rendered.contains("[Passage 2] RESTATEMENT"));
        assertTrue(rendered.contains("Only two siblings may benefit."));
    }

    @Test
    void guidelineIsLabelledNormativeAndFaqIsLabelledRestatement() {
        ScholarshipAnswerGenerator generator = generator();

        assertTrue(generator.renderPassages(
                List.of(match(ContentType.GUIDELINE, "x"))).contains("NORMATIVE"));
        assertTrue(generator.renderPassages(
                List.of(match(ContentType.FAQ, "x"))).contains("RESTATEMENT"));
        assertTrue(generator.renderPassages(
                List.of(match(ContentType.ANNEXURE, "x"))).contains("NORMATIVE"));
    }

    @Test
    void everyPassageIsNumberedForCitation() {
        ScholarshipAnswerGenerator generator = generator();

        String rendered = generator.renderPassages(List.of(
                match(ContentType.GUIDELINE, "one"),
                match(ContentType.GUIDELINE, "two"),
                match(ContentType.GUIDELINE, "three")));

        for (int i = 1; i <= 3; i++) {
            assertTrue(rendered.contains("[Passage " + i + "]"), "missing passage " + i);
        }
        assertFalse(rendered.contains("[Passage 4]"));
    }

    /**
     * A model built against an unreachable host. It is never called, because
     * the tests that use it only exercise the no-context and rendering paths.
     */
    private static ScholarshipAnswerGenerator generator() {
        return new ScholarshipAnswerGenerator(dev.langchain4j.model.openai.OpenAiChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("unused")
                .modelName("unused")
                .maxRetries(0)
                .build());
    }
}