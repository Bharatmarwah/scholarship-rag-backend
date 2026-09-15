package com.bharat.scholarship_rag_backend.memory;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MemoryManager {

    private final ConversationMemory conversationMemory;

    public MemoryManager(ConversationMemory conversationMemory){
        this.conversationMemory=conversationMemory;
    }

    public void addUserMessage(ChatRequest chatRequest){

        ChatMessage message = new ChatMessage();
        message.setRole(MessageRole.USER);
        message.setContent(chatRequest.getQuery());
        message.setCreatedAt(LocalDateTime.now());

        conversationMemory.addMessage(chatRequest.getConversationId(),message);

    }

    public void addAssistantMessage(String conversationId ,ChatResponse chatResponse){
        ChatMessage message = new ChatMessage();
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(chatResponse.getConversationSummary());
        message.setCreatedAt(LocalDateTime.now());

        conversationMemory.addMessage(conversationId,message);
    }



}
