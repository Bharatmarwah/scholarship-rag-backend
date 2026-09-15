package com.bharat.scholarship_rag_backend.memory.conversation;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationMemory {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MemoryConstants memoryConstants;

    public ConversationMemory(RedisTemplate<String, Object> redisTemplate, MemoryConstants memoryConstants) {
        this.redisTemplate = redisTemplate;
        this.memoryConstants = memoryConstants;
    }

    public void addMessage(String conversationId, ChatMessage message) {

        String key = MemoryConstants.SESSION_PREFIX + conversationId;

        redisTemplate
                .opsForList()
                .rightPush(key, message);

        redisTemplate.expire(key, memoryConstants.getTtl());
    }

    @SuppressWarnings("unchecked")
    public List<ChatMessage> allRecentConversation(String conversationId){
        String key = MemoryConstants.SESSION_PREFIX + conversationId;
        return (List<ChatMessage>)
                        (List<?>)
                                redisTemplate
                                        .opsForList()
                                        .range(key, -10, -1);
    }
}
