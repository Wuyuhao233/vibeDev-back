package com.vibedev.service;

import com.vibedev.dto.sensitive.SensitiveCheckRequest;
import com.vibedev.entity.SensitiveWord;
import com.vibedev.repository.SensitiveWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceTest {

    @Mock SensitiveWordRepository repo;
    @Mock StringRedisTemplate redis;
    @Mock RedisMessageListenerContainer listenerContainer;

    private SensitiveWordService service;

    @BeforeEach
    void setUp() {
        service = new SensitiveWordService(repo, redis, listenerContainer);
        // Skip @PostConstruct init by manually refreshing
    }

    @Test
    void checkShouldReturnEmptyForCleanContent() {
        when(repo.findByIsActiveTrue()).thenReturn(List.of());

        service.refreshCache();
        var resp = service.check(new SensitiveCheckRequest("正常内容"));

        assertFalse(resp.hasSensitive());
        assertTrue(resp.matches().isEmpty());
    }

    @Test
    void checkShouldFindSensitiveWords() {
        var word = new SensitiveWord();
        word.setWord("敏感");
        word.setActive(true);
        when(repo.findByIsActiveTrue()).thenReturn(List.of(word));

        service.refreshCache();
        var resp = service.check(new SensitiveCheckRequest("这是敏感内容"));

        assertTrue(resp.hasSensitive());
        assertFalse(resp.matches().isEmpty());
        assertEquals(1, resp.matches().size());
        assertEquals("敏感", resp.matches().get(0).word());
    }

    @Test
    void hasSensitiveWordShouldReturnTrue() {
        var word = new SensitiveWord();
        word.setWord("bad");
        word.setActive(true);
        when(repo.findByIsActiveTrue()).thenReturn(List.of(word));

        service.refreshCache();
        assertTrue(service.hasSensitiveWord("this is bad"));
        assertFalse(service.hasSensitiveWord("this is good"));
    }

    @Test
    void hasSensitiveWordShouldReturnFalseForNull() {
        assertFalse(service.hasSensitiveWord(null));
        assertFalse(service.hasSensitiveWord(""));
    }

    @Test
    void publishRefreshShouldSendRedisMessage() {
        service.publishRefresh();
        verify(redis).convertAndSend(eq("sensitive-words:refresh"), eq("refresh"));
    }
}
