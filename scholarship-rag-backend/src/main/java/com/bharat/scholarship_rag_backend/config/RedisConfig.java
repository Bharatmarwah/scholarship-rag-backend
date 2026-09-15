package com.bharat.scholarship_rag_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();

        template.setConnectionFactory(redisConnectionFactory);

        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();

        template.setKeySerializer(
                new StringRedisSerializer()
        );

        template.setValueSerializer(
                new GenericJacksonJsonRedisSerializer(mapper)
        );

        template.afterPropertiesSet();

        return template;
    }
}