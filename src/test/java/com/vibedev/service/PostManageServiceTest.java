package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.post.*;
import com.vibedev.entity.Board;
import com.vibedev.entity.Post;
import com.vibedev.repository.BoardRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.PostTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostManageServiceTest {

    @Mock private PostRepository postRepo;
    @Mock private BoardRepository boardRepo;
    @Mock private PostTagRepository postTagRepo;
    @Mock private PostService postService;

    @InjectMocks
    private PostManageService service;

    private Post createPost(String id, String title, String boardId, boolean deleted) {
        var p = new Post();
        p.setId(id);
        p.setTitle(title);
        p.setBoardId(boardId);
        p.setAuthorId("author1");
        p.setContentMarkdown("content");
        p.setContentHtml("<p>content</p>");
        p.setDeleted(deleted);
        return p;
    }

    @Test
    void listPosts_returnsPaginated() {
        var p1 = createPost("p1", "Hello", "b1", false);
        var p2 = createPost("p2", "World", "b1", false);
        Page<Post> page = new PageImpl<>(List.of(p1, p2));
        when(postRepo.findPostsForAdmin(isNull(), eq("active"), isNull(), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listPosts(null, "active", null, 1, 20);
        assertEquals(2, result.items().size());
    }

    @Test
    void listPosts_withDeletedIncluded() {
        var p1 = createPost("p1", "Hello", "b1", true);
        Page<Post> page = new PageImpl<>(List.of(p1));
        when(postRepo.findPostsForAdmin(isNull(), eq("deleted"), isNull(), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listPosts(null, "deleted", null, 1, 20);
        assertEquals(1, result.total());
    }

    @Test
    void forceDelete_notFound_throwsError() {
        when(postRepo.findById("notexist")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> service.forceDelete("notexist", "admin1", "admin"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void forceDelete_alreadyDeleted_throwsError() {
        var p = createPost("p1", "Title", "b1", true);
        when(postRepo.findById("p1")).thenReturn(Optional.of(p));

        var ex = assertThrows(BusinessException.class, () -> service.forceDelete("p1", "admin1", "admin"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void forceDelete_success() {
        var p = createPost("p1", "Title", "b1", false);
        when(postRepo.findById("p1")).thenReturn(Optional.of(p));

        service.forceDelete("p1", "admin1", "admin");

        assertTrue(p.isDeleted());
        assertEquals("admin1", p.getDeletedBy());
        verify(postRepo).save(p);
    }

    @Test
    void movePost_targetNotFound_throwsError() {
        var p = createPost("p1", "Title", "b1", false);
        when(postRepo.findById("p1")).thenReturn(Optional.of(p));
        when(boardRepo.findById("b2")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> service.movePost("p1", "b2"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void movePost_sameBoard_throwsError() {
        var p = createPost("p1", "Title", "b1", false);
        when(postRepo.findById("p1")).thenReturn(Optional.of(p));
        var target = new Board();
        target.setId("b1");
        when(boardRepo.findById("b1")).thenReturn(Optional.of(target));

        var ex = assertThrows(BusinessException.class, () -> service.movePost("p1", "b1"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void movePost_success() {
        var p = createPost("p1", "Title", "b1", false);
        when(postRepo.findById("p1")).thenReturn(Optional.of(p));
        var target = new Board();
        target.setId("b2");
        when(boardRepo.findById("b2")).thenReturn(Optional.of(target));
        when(postTagRepo.findByPostId("p1")).thenReturn(List.of());

        service.movePost("p1", "b2");

        assertEquals("b2", p.getBoardId());
        verify(postRepo).save(p);
    }

    @Test
    void togglePin_delegatesToPostService() {
        when(postService.togglePin("p1", "global", "admin1", "admin")).thenReturn(null);

        service.togglePin("p1", "global", "admin1", "admin");

        verify(postService).togglePin("p1", "global", "admin1", "admin");
    }

    @Test
    void toggleEssence_delegatesToPostService() {
        when(postService.toggleEssence("p1", "admin1", "admin")).thenReturn(null);

        service.toggleEssence("p1", "admin1", "admin");

        verify(postService).toggleEssence("p1", "admin1", "admin");
    }
}
