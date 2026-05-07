package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.board.*;
import com.vibedev.entity.Board;
import com.vibedev.entity.Tag;
import com.vibedev.repository.BoardRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardManageServiceTest {

    @Mock private BoardRepository boardRepo;
    @Mock private TagRepository tagRepo;
    @Mock private PostRepository postRepo;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private BoardManageService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    private Board createBoard(String id, String name, int sortOrder) {
        var b = new Board();
        b.setId(id);
        b.setName(name);
        b.setSortOrder(sortOrder);
        b.setStatus("active");
        b.setIcon("");
        b.setDescription("");
        return b;
    }

    private Tag createTag(String id, String boardId, String name, int sortOrder) {
        var t = new Tag();
        t.setId(id);
        t.setBoardId(boardId);
        t.setName(name);
        t.setSortOrder(sortOrder);
        return t;
    }

    // ── listAll ────────────────────────────────────

    @Test
    void listAll_returnsAllBoards() {
        var b1 = createBoard("b1", "Frontend", 1);
        var b2 = createBoard("b2", "Backend", 2);
        when(boardRepo.findAll()).thenReturn(List.of(b1, b2));
        when(tagRepo.findByBoardIdOrderBySortOrder("b1")).thenReturn(List.of());
        when(tagRepo.findByBoardIdOrderBySortOrder("b2")).thenReturn(List.of());

        var result = service.listAll();
        assertEquals(2, result.size());
    }

    // ── create ─────────────────────────────────────

    @Test
    void create_duplicateName_throwsError() {
        var existing = createBoard("b1", "Frontend", 1);
        when(boardRepo.findAll()).thenReturn(List.of(existing));

        var dto = new CreateBoardRequest("Frontend", null, null, null);
        var ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void create_success() {
        when(boardRepo.findAll()).thenReturn(List.of());
        when(tagRepo.findByBoardIdOrderBySortOrder(anyString())).thenReturn(List.of());

        var dto = new CreateBoardRequest("DevOps", "icon.svg", "CI/CD board", List.of("Docker"));
        var result = service.create(dto);

        assertNotNull(result);
        assertEquals("DevOps", result.name());
        assertEquals(1, result.sortOrder());
        verify(boardRepo, times(1)).save(any(Board.class));
        verify(tagRepo).save(any(Tag.class));
    }

    // ── update ─────────────────────────────────────

    @Test
    void update_notFound_throwsError() {
        when(boardRepo.findById("notexist")).thenReturn(Optional.empty());

        var dto = new UpdateBoardRequest("New Name", null, null, null);
        var ex = assertThrows(BusinessException.class, () -> service.update("notexist", dto));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void update_success() {
        var b = createBoard("b1", "Old", 1);
        when(boardRepo.findById("b1")).thenReturn(Optional.of(b));
        when(boardRepo.findAll()).thenReturn(List.of(b));

        var dto = new UpdateBoardRequest("New", null, null, null);
        service.update("b1", dto);

        assertEquals("New", b.getName());
        verify(boardRepo).save(b);
    }

    // ── softDelete ─────────────────────────────────

    @Test
    void softDelete_success() {
        var b = createBoard("b1", "Test", 1);
        when(boardRepo.findById("b1")).thenReturn(Optional.of(b));

        service.softDelete("b1");

        assertTrue(b.isDeleted());
        assertEquals("inactive", b.getStatus());
        verify(boardRepo).save(b);
    }

    // ── sort ───────────────────────────────────────

    @Test
    void sort_success() {
        var b1 = createBoard("b1", "A", 1);
        var b2 = createBoard("b2", "B", 2);
        when(boardRepo.findById("b1")).thenReturn(Optional.of(b1));
        when(boardRepo.findById("b2")).thenReturn(Optional.of(b2));

        var dto = new BoardSortRequest(List.of(
                new BoardSortItem("b1", 2),
                new BoardSortItem("b2", 1)));
        service.sort(dto);

        assertEquals(2, b1.getSortOrder());
        assertEquals(1, b2.getSortOrder());
    }

    // ── addTag ─────────────────────────────────────

    @Test
    void addTag_duplicateName_throwsError() {
        var b = createBoard("b1", "Test", 1);
        var existingTag = createTag("t1", "b1", "Docker", 0);
        when(boardRepo.findById("b1")).thenReturn(Optional.of(b));
        when(tagRepo.findByBoardIdOrderBySortOrder("b1")).thenReturn(List.of(existingTag));

        var dto = new CreateTagRequest("Docker");
        var ex = assertThrows(BusinessException.class, () -> service.addTag("b1", dto));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void addTag_success() {
        var b = createBoard("b1", "Test", 1);
        when(boardRepo.findById("b1")).thenReturn(Optional.of(b));
        when(tagRepo.findByBoardIdOrderBySortOrder("b1")).thenReturn(List.of());

        var dto = new CreateTagRequest("K8s");
        var result = service.addTag("b1", dto);

        assertNotNull(result);
        verify(tagRepo).save(any(Tag.class));
    }

    // ── updateTag ──────────────────────────────────

    @Test
    void updateTag_notFound_throwsError() {
        when(tagRepo.findById("notexist")).thenReturn(Optional.empty());

        var dto = new UpdateTagRequest("New", null);
        var ex = assertThrows(BusinessException.class, () -> service.updateTag("notexist", dto));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
