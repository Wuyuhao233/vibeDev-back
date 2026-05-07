package com.vibedev.service;

import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.PostCard;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedServiceTest {

    @Mock PostRepository postRepo;
    @Mock PostTagRepository postTagRepo;
    @Mock TagRepository tagRepo;
    @Mock UserRepository userRepo;
    @Mock BoardRepository boardRepo;
    @Mock BoardService boardService;

    FeedService feedService;

    @BeforeEach
    void setUp() {
        feedService = new FeedService(postRepo, postTagRepo, tagRepo,
                userRepo, boardRepo, boardService);
    }

    private Post createPost(String id, String title, String authorId, String boardId) {
        var p = new Post();
        p.setId(id);
        p.setTitle(title);
        p.setContentMarkdown("content for " + title);
        p.setAuthorId(authorId);
        p.setBoardId(boardId);
        p.setCreatedAt(Instant.now());
        return p;
    }

    // ─── feedTrending ─────────────────────────────────────

    @Test
    void feedTrending_shouldReturnPagedPosts() {
        var post = createPost("p1", "Trending Post", "u1", "b1");
        var postsPage = new PageImpl<>(List.of(post));
        when(postRepo.findTrending(any(), any())).thenReturn(postsPage);

        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of());
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of());

        var result = feedService.feedTrending(1, 20);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        verify(postRepo).findTrending(any(), any());
    }

    @Test
    void feedTrending_shouldCapLimitAt50() {
        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of()));

        var result = feedService.feedTrending(1, 100);

        assertNotNull(result);
    }

    @Test
    void feedTrending_shouldReturnEmptyForNoPosts() {
        when(postRepo.findTrending(any(), any())).thenReturn(new PageImpl<>(List.of()));

        var result = feedService.feedTrending(1, 20);

        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
    }

    // ─── feedRecommend (V1.0 degraded to trending) ───────

    @Test
    void feedRecommend_shouldDegradeToTrending() {
        var post = createPost("p1", "Rec Post", "u1", "b1");
        var postsPage = new PageImpl<>(List.of(post));
        when(postRepo.findTrending(any(), any())).thenReturn(postsPage);

        when(postTagRepo.findByPostIdIn(List.of("p1"))).thenReturn(List.of());
        when(userRepo.findAllById(List.of("u1"))).thenReturn(List.of());
        when(boardRepo.findAllById(List.of("b1"))).thenReturn(List.of());

        var result = feedService.feedRecommend("u1", 1, 20);

        assertEquals(1, result.items().size());
        verify(postRepo).findTrending(any(), any());
    }

    // ─── feedFollowing (V1.0 placeholder) ────────────────

    @Test
    void feedFollowing_shouldReturnEmptyList() {
        var result = feedService.feedFollowing("u1", 1, 20);

        assertEquals(0, result.items().size());
        assertEquals(0, result.total());
        verify(postRepo, never()).findByTagIds(anyList(), any());
    }

    @Test
    void feedFollowing_shouldReturnEmptyForAnyUser() {
        var result = feedService.feedFollowing(null, 1, 20);

        assertEquals(0, result.items().size());
    }
}
