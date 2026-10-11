package com.bharat.scholarship_rag_backend.intent;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentClassificationTest {

    private final OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void feedsConversationHistoryIntoThePromptForAnAnsweringTurn() {
        ChatMessage validationQuestion = new ChatMessage(
                MessageRole.ASSISTANT,
                "Could you please tell me which year of your course you are currently in?",
                LocalDateTime.now());

        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"type\":\"SCHOLARSHIP\"}");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify(
                "I am in 3rd year of B.Tech.",
                List.of(validationQuestion));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(captor.capture());
        String prompt = captor.getValue();

        assertTrue(prompt.contains(
                "ASSISTANT: Could you please tell me which year of your course you are currently in?"));
        assertTrue(prompt.contains(
                "answers that provide profile or eligibility details"));
        assertEquals(IntentType.SCHOLARSHIP, response.getType());
    }

    @Test
    void returnsUnknownWhenModelResponseCannotBeParsed() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("not json");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify("hello", List.of());

        assertEquals(IntentType.UNKNOWN, response.getType());
    }
}