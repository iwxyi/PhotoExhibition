package com.photoexhibition.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Redis缓存管理器（当Redis可用时使用）
     */
    @Bean
    @ConditionalOnProperty(name = "spring.redis.host", havingValue = "localhost", matchIfMissing = true)
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        try {
            // 测试Redis连接
            connectionFactory.getConnection().ping();

            // 配置ObjectMapper以支持Java 8时间类型
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

            return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
        } catch (Exception e) {
            // Redis不可用，返回null让Spring使用备用缓存管理器
            return null;
        }
    }

    /**
     * 内存缓存管理器（Redis不可用时的备用方案）
     */
    @Bean
    @ConditionalOnProperty(name = "spring.redis.host", havingValue = "disabled", matchIfMissing = false)
    public CacheManager concurrentMapCacheManager() {
        return new ConcurrentMapCacheManager();
    }
}

