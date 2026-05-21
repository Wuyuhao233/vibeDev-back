package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.sensitive.AddSensitiveWordRequest;
import com.vibedev.dto.admin.sensitive.UpdateSensitiveWordRequest;
import com.vibedev.entity.SensitiveWord;
import com.vibedev.repository.SensitiveWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceAdminTest {

    @Mock private SensitiveWordRepository repo;
    @Mock private StringRedisTemplate redis;
    @Mock private RedisMessageListenerContainer listenerContainer;

    private SensitiveWordService service;

    @BeforeEach
    void setUp() {
        service = new SensitiveWordService(repo, redis, listenerContainer);
    }

    private SensitiveWord createWord(String id, String word) {
        var sw = new SensitiveWord();
        sw.setId(id);
        sw.setWord(word);
        sw.setMatchType("exact");
        sw.setActive(true);
        return sw;
    }

    @Test
    void list_returnsPaginated() {
        var sw1 = createWord("1", "spam");
        var sw2 = createWord("2", "ad");
        Page<SensitiveWord> page = new PageImpl<>(List.of(sw1, sw2));
        when(repo.findByWordContaining(isNull(), any(Pageable.class))).thenReturn(page);

        var result = service.list(null, 1, 50);
        assertEquals(2, result.items().size());
    }

    @Test
    void add_duplicateWord_throwsError() {
        when(repo.existsByWord("spam")).thenReturn(true);

        var ex = assertThrows(BusinessException.class, () -> service.add("spam", "exact", null, "admin1"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void add_success() {
        when(repo.existsByWord("spam")).thenReturn(false);

        var result = service.add("spam", "fuzzy", null, "admin1");

        assertNotNull(result);
        assertEquals("spam", result.word());
        verify(repo).save(any(SensitiveWord.class));
    }

    @Test
    void update_notFound_throwsError() {
        when(repo.findById("notexist")).thenReturn(Optional.empty());

        var dto = new UpdateSensitiveWordRequest("new", null, null);
        var ex = assertThrows(BusinessException.class, () -> service.update("notexist", dto));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void toggle_success() {
        var sw = createWord("1", "spam");
        when(repo.findById("1")).thenReturn(Optional.of(sw));

        var result = service.toggle("1");

        assertFalse(result.isActive());
        verify(repo).save(sw);
    }

    @Test
    void batchImport_skipsDuplicates() {
        when(repo.existsByWord("spam")).thenReturn(true);
        when(repo.existsByWord("ad")).thenReturn(false);

        var words = List.of(
                new AddSensitiveWordRequest("spam", "exact", null),
                new AddSensitiveWordRequest("ad", "fuzzy", null));
        var result = service.batchImport(words, "admin1");

        assertEquals(1, result.size());
        assertEquals("ad", result.get(0).word());
    }
}
