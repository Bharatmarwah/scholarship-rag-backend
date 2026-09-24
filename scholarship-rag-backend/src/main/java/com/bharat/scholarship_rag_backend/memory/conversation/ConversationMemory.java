package com.bharat.scholarship_rag_backend.memory.conversation;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationMemory {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ConversationMemoryConstants conversationMemoryConstants;

    public ConversationMemory(RedisTemplate<String, Object> redisTemplate, ConversationMemoryConstants conversationMemoryConstants) {
        this.redisTemplate = redisTemplate;
        this.conversationMemoryConstants = conversationMemoryConstants;
    }

    public void addMessage(String conversationId, ChatMessage message) {

        String key = ConversationMemoryConstants.SESSION_PREFIX + conversationId;

        redisTemplate
                .opsForList()
                .rightPush(key, message);

        redisTemplate.expire(key, conversationMemoryConstants.getTtl());
    }

    @SuppressWarnings("unchecked")
    public List<ChatMessage> allRecentConversation(String conversationId){
        String key = ConversationMemoryConstants.SESSION_PREFIX + conversationId;
        List<ChatMessage> messages =
                (List<ChatMessage>)
                        (List<?>)
                                redisTemplate
                                        .opsForList()
                                        .range(key, -10, -1);
        return messages == null ? List.of() : messages;
    }
}
