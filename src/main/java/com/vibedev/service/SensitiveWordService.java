package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.sensitive.*;
import com.vibedev.dto.sensitive.SensitiveCheckRequest;
import com.vibedev.dto.sensitive.SensitiveCheckResponse;
import com.vibedev.entity.SensitiveWord;
import com.vibedev.repository.SensitiveWordRepository;
import com.vibedev.util.SensitiveWordFilter;
import com.vibedev.util.SensitiveWordFilter.MatchResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        listenerContainer.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                log.info("Received sensitive word refresh signal");
                refreshCache();
            }
        }, new ChannelTopic(REDIS_CHANNEL));
    }

    // ── Cache / Pub-Sub ────────────────────────────

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

    // ── Content checking ──────────────────────────

    public SensitiveCheckResponse check(SensitiveCheckRequest request) {
        List<MatchResult> matches = filter.findMatches(request.content());
        if (matches.isEmpty()) {
            return new SensitiveCheckResponse(false, List.of());
        }
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

    // ── Admin CRUD ────────────────────────────────

    public com.vibedev.common.PaginatedResponse<SensitiveWordItem> list(
            String search, int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 100) limit = 50;

        var pageable = PageRequest.of(page - 1, limit);
        var result = repo.findByWordContaining(
                search != null && !search.isBlank() ? search : null, pageable);

        var items = result.getContent().stream()
                .map(SensitiveWordItem::from)
                .toList();

        return com.vibedev.common.PaginatedResponse.of(items, result.getTotalElements(), page, limit);
    }

    @Transactional
    public SensitiveWordItem add(String word, String matchType, String category, String createdBy) {
        if (repo.existsByWord(word)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该敏感词已存在");
        }
        var entity = new SensitiveWord();
        entity.setId(UUID.randomUUID().toString());
        entity.setWord(word);
        entity.setMatchType(matchType != null ? matchType : "exact");
        entity.setCategory(category);
        entity.setCreatedBy(createdBy);
        entity.setActive(true);
        repo.save(entity);

        refreshCache();
        publishRefresh();

        return SensitiveWordItem.from(entity);
    }

    @Transactional
    public void update(String id, UpdateSensitiveWordRequest dto) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "敏感词不存在"));

        if (dto.word() != null) {
            if (!entity.getWord().equals(dto.word()) && repo.existsByWord(dto.word())) {
                throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该敏感词已存在");
            }
            entity.setWord(dto.word());
        }
        if (dto.matchType() != null) {
            entity.setMatchType(dto.matchType());
        }
        if (dto.category() != null) {
            entity.setCategory(dto.category());
        }
        repo.save(entity);

        refreshCache();
        publishRefresh();
    }

    @Transactional
    public void delete(String id) {
        if (!repo.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
        repo.deleteById(id);

        refreshCache();
        publishRefresh();
    }

    @Transactional
    public SensitiveWordItem toggle(String id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "敏感词不存在"));
        entity.setActive(!entity.isActive());
        repo.save(entity);

        refreshCache();
        publishRefresh();

        return SensitiveWordItem.from(entity);
    }

    @Transactional
    public List<SensitiveWordItem> batchImport(List<AddSensitiveWordRequest> words, String createdBy) {
        List<SensitiveWordItem> result = new ArrayList<>();
        for (var w : words) {
            if (!repo.existsByWord(w.word())) {
                var entity = new SensitiveWord();
                entity.setId(UUID.randomUUID().toString());
                entity.setWord(w.word());
                entity.setMatchType(w.matchType() != null ? w.matchType() : "exact");
                entity.setCategory(w.category());
                entity.setCreatedBy(createdBy);
                entity.setActive(true);
                repo.save(entity);
                result.add(SensitiveWordItem.from(entity));
            }
        }

        if (!result.isEmpty()) {
            refreshCache();
            publishRefresh();
        }

        return result;
    }
}
