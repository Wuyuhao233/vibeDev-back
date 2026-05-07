package com.vibedev.service;

import com.vibedev.dto.sensitive.SensitiveCheckRequest;
import com.vibedev.dto.sensitive.SensitiveCheckResponse;
import com.vibedev.repository.SensitiveWordRepository;
import com.vibedev.util.SensitiveWordFilter;
import com.vibedev.util.SensitiveWordFilter.MatchResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SensitiveWordService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordService.class);
    private static final String REDIS_CHANNEL = "sensitive-words:refresh";

    private final SensitiveWordRepository repo;
    private final SensitiveWordFilter filter;
    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listenerContainer;

    public SensitiveWordService(SensitiveWordRepository repo,
                                StringRedisTemplate redis,
                                RedisMessageListenerContainer listenerContainer) {
        this.repo = repo;
        this.filter = new SensitiveWordFilter();
        this.redis = redis;
        this.listenerContainer = listenerContainer;
    }

    @PostConstruct
    public void init() {
        refreshCache();
        // Subscribe to Redis refresh messages
        listenerContainer.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                log.info("Received sensitive word refresh signal");
                refreshCache();
            }
        }, new ChannelTopic(REDIS_CHANNEL));
    }

    public void refreshCache() {
        var words = repo.findByIsActiveTrue();
        List<String> wordList = words.stream()
                .map(w -> w.getWord())
                .collect(Collectors.toList());
        filter.rebuild(wordList);
        log.info("Sensitive word cache refreshed: {} words loaded", wordList.size());
    }

    public void publishRefresh() {
        try {
            redis.convertAndSend(REDIS_CHANNEL, "refresh");
        } catch (Exception e) {
            log.warn("Failed to publish sensitive word refresh: {}", e.getMessage());
        }
    }

    public SensitiveCheckResponse check(SensitiveCheckRequest request) {
        List<MatchResult> matches = filter.findMatches(request.content());
        if (matches.isEmpty()) {
            return new SensitiveCheckResponse(false, List.of());
        }
        // Group positions by word
        Map<String, List<Integer>> wordPositions = new LinkedHashMap<>();
        for (MatchResult m : matches) {
            wordPositions.computeIfAbsent(m.word(), k -> new ArrayList<>()).add(m.position());
        }
        List<SensitiveCheckResponse.MatchItem> items = wordPositions.entrySet().stream()
                .map(e -> new SensitiveCheckResponse.MatchItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return new SensitiveCheckResponse(true, items);
    }

    public boolean hasSensitiveWord(String content) {
        if (content == null || content.isBlank()) return false;
        return filter.hasSensitive(content);
    }
}
