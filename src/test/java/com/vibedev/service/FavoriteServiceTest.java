package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.favorite.*;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoriteServiceTest {

    @Mock FavoriteRepository favoriteRepo;
    @Mock CollectionFolderRepository folderRepo;
    @Mock PostRepository postRepo;
    @Mock UserRepository userRepo;
    @Mock BoardRepository boardRepo;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepo, folderRepo,
                postRepo, userRepo, boardRepo);
    }

    private User createUser(String id, String role, int level) {
        var u = new User();
        u.setId(id);
        u.setUsername("user_" + id);
        u.setNickname("Nick" + id);
        u.setAvatarUrl("/avatar.png");
        u.setRole(role);
        u.setLevel(level);
        u.setPoints(100);
        u.setBanned(false);
        return u;
    }

    private CollectionFolder createFolder(String id, String userId, String name, int version) {
        var f = new CollectionFolder();
        f.setId(id);
        f.setUserId(userId);
        f.setName(name);
        f.setVersion(version);
        return f;
    }

    // ─── createFolder ────────────────────────────────────

    @Test
    void createFolder_shouldSucceed() {
        var user = createUser("u1", "user", 3);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(folderRepo.existsByUserIdAndName("u1", "My Folder")).thenReturn(false);

        var result = favoriteService.createFolder("u1", "My Folder");

        assertNotNull(result);
        assertEquals("My Folder", result.name());
        assertEquals("u1", result.userId());
        verify(folderRepo).save(any(CollectionFolder.class));
    }

    @Test
    void createFolder_shouldFailWhenLevelBelow3() {
        var user = createUser("u1", "user", 2);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.createFolder("u1", "My Folder"));
        assertEquals(ErrorCode.INSUFFICIENT_LEVEL.getCode(), ex.getCode());
    }

    @Test
    void createFolder_shouldFailWhenNameTooLong() {
        var user = createUser("u1", "user", 3);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.createFolder("u1", "a".repeat(21)));
        assertEquals(ErrorCode.VALIDATION_TITLE.getCode(), ex.getCode());
    }

    @Test
    void createFolder_shouldFailWhenDuplicateName() {
        var user = createUser("u1", "user", 3);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(folderRepo.existsByUserIdAndName("u1", "My Folder")).thenReturn(true);

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.createFolder("u1", "My Folder"));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void createFolder_shouldFailWhenUserNotFound() {
        when(userRepo.findById("u999")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.createFolder("u999", "Folder"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── listFolders ─────────────────────────────────────

    @Test
    void listFolders_shouldReturnFolders() {
        var f1 = createFolder("f1", "u1", "Folder A", 0);
        var f2 = createFolder("f2", "u1", "Folder B", 0);
        when(folderRepo.findByUserIdOrderByCreatedAtAsc("u1")).thenReturn(List.of(f1, f2));

        var result = favoriteService.listFolders("u1");

        assertEquals(2, result.size());
        assertEquals("Folder A", result.get(0).name());
        assertEquals("Folder B", result.get(1).name());
    }

    @Test
    void listFolders_shouldReturnEmptyWhenNoFolders() {
        when(folderRepo.findByUserIdOrderByCreatedAtAsc("u1")).thenReturn(List.of());

        var result = favoriteService.listFolders("u1");

        assertTrue(result.isEmpty());
    }

    // ─── updateFolder ────────────────────────────────────

    @Test
    void updateFolder_shouldSucceed() {
        var folder = createFolder("f1", "u1", "Old Name", 0);
        when(folderRepo.findByIdAndUserId("f1", "u1")).thenReturn(Optional.of(folder));

        var result = favoriteService.updateFolder("f1", "u1", "New Name", 0);

        assertEquals("New Name", result.name());
        verify(folderRepo).save(folder);
    }

    @Test
    void updateFolder_shouldFailOnVersionConflict() {
        var folder = createFolder("f1", "u1", "Old Name", 3);
        when(folderRepo.findByIdAndUserId("f1", "u1")).thenReturn(Optional.of(folder));

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.updateFolder("f1", "u1", "New Name", 1));
        assertEquals(ErrorCode.VERSION_CONFLICT.getCode(), ex.getCode());
    }

    @Test
    void updateFolder_shouldFailWhenFolderNotFound() {
        when(folderRepo.findByIdAndUserId("f999", "u1")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.updateFolder("f999", "u1", "New Name", 0));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateFolder_shouldFailWhenNameTooLong() {
        var folder = createFolder("f1", "u1", "Old Name", 0);
        when(folderRepo.findByIdAndUserId("f1", "u1")).thenReturn(Optional.of(folder));

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.updateFolder("f1", "u1", "a".repeat(21), 0));
        assertEquals(ErrorCode.VALIDATION_TITLE.getCode(), ex.getCode());
    }

    // ─── deleteFolder ────────────────────────────────────

    @Test
    void deleteFolder_shouldSucceed() {
        var folder = createFolder("f1", "u1", "My Folder", 0);
        var fav1 = new Favorite();
        fav1.setId("fav1");
        fav1.setUserId("u1");
        fav1.setPostId("p1");
        fav1.setCollectionFolderId("f1");

        when(folderRepo.findByIdAndUserId("f1", "u1")).thenReturn(Optional.of(folder));
        when(favoriteRepo.findByCollectionFolderId("f1")).thenReturn(List.of(fav1));

        assertDoesNotThrow(() -> favoriteService.deleteFolder("f1", "u1"));

        verify(favoriteRepo).save(fav1); // moved to default
        verify(folderRepo).delete(folder);
    }

    @Test
    void deleteFolder_shouldFailWhenFolderNotFound() {
        when(folderRepo.findByIdAndUserId("f999", "u1")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.deleteFolder("f999", "u1"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── listFavorites ───────────────────────────────────

    @Test
    void listFavorites_shouldFilterByFolderId() {
        var user = createUser("u1", "user", 3);
        var fav = new Favorite();
        fav.setId("fav1");
        fav.setUserId("u1");
        fav.setPostId("p1");
        fav.setCollectionFolderId("f1");

        var post = new Post();
        post.setId("p1");
        post.setTitle("Test Post");
        post.setAuthorId("u2");
        post.setBoardId("b1");
        post.setDeleted(false);

        var author = createUser("u2", "user", 1);
        var board = new Board();
        board.setId("b1");
        board.setName("Test Board");

        when(favoriteRepo.findByUserIdAndCollectionFolderIdOrderByCreatedAtDesc(
                anyString(), anyString(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(fav)));
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));
        when(userRepo.findById("u2")).thenReturn(Optional.of(author));
        when(boardRepo.findById("b1")).thenReturn(Optional.of(board));

        var result = favoriteService.listFavorites("u1", 1, 20, "f1");

        assertEquals(1, result.items().size());
        assertEquals("Test Post", result.items().get(0).post().title());
    }

    // ─── moveFavorites ───────────────────────────────────

    @Test
    void moveFavorites_shouldSucceed() {
        var user = createUser("u1", "user", 3);
        var targetFolder = createFolder("f2", "u1", "Target", 0);
        var fav1 = new Favorite();
        fav1.setId("fav1");
        fav1.setUserId("u1");
        fav1.setPostId("p1");
        var fav2 = new Favorite();
        fav2.setId("fav2");
        fav2.setUserId("u1");
        fav2.setPostId("p2");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(folderRepo.findByIdAndUserId("f2", "u1")).thenReturn(Optional.of(targetFolder));
        when(favoriteRepo.findByUserIdAndPostId("u1", "p1")).thenReturn(Optional.of(fav1));
        when(favoriteRepo.findByUserIdAndPostId("u1", "p2")).thenReturn(Optional.of(fav2));

        var result = favoriteService.moveFavorites("u1", List.of("p1", "p2"), "f2");

        assertEquals(2, result.movedCount());
        assertEquals("f2", fav1.getCollectionFolderId());
        assertEquals("f2", fav2.getCollectionFolderId());
        verify(favoriteRepo, times(2)).save(any(Favorite.class));
    }

    @Test
    void moveFavorites_shouldFailWhenLevelBelow3() {
        var user = createUser("u1", "user", 2);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.moveFavorites("u1", List.of("p1"), "f1"));
        assertEquals(ErrorCode.INSUFFICIENT_LEVEL.getCode(), ex.getCode());
    }

    @Test
    void moveFavorites_toNull_shouldSucceed() {
        var user = createUser("u1", "user", 3);
        var fav1 = new Favorite();
        fav1.setId("fav1");
        fav1.setUserId("u1");
        fav1.setPostId("p1");
        fav1.setCollectionFolderId("f1");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(favoriteRepo.findByUserIdAndPostId("u1", "p1")).thenReturn(Optional.of(fav1));

        var result = favoriteService.moveFavorites("u1", List.of("p1"), null);

        assertEquals(1, result.movedCount());
        assertNull(fav1.getCollectionFolderId());
    }

    @Test
    void moveFavorites_shouldFailWhenTargetFolderNotFound() {
        var user = createUser("u1", "user", 3);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(folderRepo.findByIdAndUserId("f999", "u1")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.moveFavorites("u1", List.of("p1"), "f999"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void moveFavorites_shouldFailWhenNoFavoritesFound() {
        var user = createUser("u1", "user", 3);
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(favoriteRepo.findByUserIdAndPostId("u1", "p1")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> favoriteService.moveFavorites("u1", List.of("p1"), null));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
