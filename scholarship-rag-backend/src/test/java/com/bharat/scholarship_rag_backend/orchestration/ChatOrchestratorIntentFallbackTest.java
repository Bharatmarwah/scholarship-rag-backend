package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOrchestratorIntentFallbackTest {

    private ChatMessage message(MessageRole role, String content) {
        return new ChatMessage(role, content, LocalDateTime.now());
    }

    @Test
    void answerAfterAssistantsQuestionIsDetected() {
        assertTrue(ChatOrchestrator.isAnswerToAssistantsQuestion(List.of(
                message(MessageRole.ASSISTANT,
                        "Could you please tell me which year of your course you are currently in?"))));
    }

    @Test
    void userTurnAsLastMessageIsNotFlagsAssistantsAnswer() {
        assertFalse(ChatOrchestrator.isAnswerToAssistantsQuestion(List.of(
                message(MessageRole.USER, "I want to apply for a scholarship"))));
    }

    @Test
    void nonQuestionAssistantTurnIsNotFlagsAnswer() {
        assertFalse(ChatOrchestrator.isAnswerToAssistantsQuestion(List.of(
                message(MessageRole.ASSISTANT, "Thank you for checking"))));
    }

    @Test
    void emptyHistoryIsNotFlagsAnswer() {
        assertFalse(ChatOrchestrator.isAnswerToAssistantsQuestion(List.of()));
    }

    @Test
    void informationalQuestionsAreRecognised() {
        assertTrue(ChatOrchestrator.isInformationalQuestion("what is social category"));
        assertTrue(ChatOrchestrator.isInformationalQuestion("what is your income limit?"));
        assertTrue(ChatOrchestrator.isInformationalQuestion("tell me about PM YASASVI"));
        assertTrue(ChatOrchestrator.isInformationalQuestion("explain the eligibility criteria"));
        assertTrue(ChatOrchestrator.isInformationalQuestion("how do I apply?"));
    }

    @Test
    void plainAnswersAreNotMistakenForQuestions() {
        assertFalse(ChatOrchestrator.isInformationalQuestion("btech"));
        assertFalse(ChatOrchestrator.isInformationalQuestion("I am in 3rd year of B.Tech"));
        assertFalse(ChatOrchestrator.isInformationalQuestion("obc"));
        assertFalse(ChatOrchestrator.isInformationalQuestion("Delhi"));
        assertFalse(ChatOrchestrator.isInformationalQuestion("5 lakh"));
        assertFalse(ChatOrchestrator.isInformationalQuestion(""));
    }
}