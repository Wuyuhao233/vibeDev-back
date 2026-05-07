package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.board.*;
import com.vibedev.entity.Board;
import com.vibedev.entity.Tag;
import com.vibedev.repository.BoardRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BoardManageService {

    private static final Logger log = LoggerFactory.getLogger(BoardManageService.class);
    private static final String BOARDS_CACHE_KEY = "boards:all";

    private final BoardRepository boardRepo;
    private final TagRepository tagRepo;
    private final PostRepository postRepo;
    private final StringRedisTemplate redis;

    public BoardManageService(BoardRepository boardRepo, TagRepository tagRepo,
                               PostRepository postRepo, StringRedisTemplate redis) {
        this.boardRepo = boardRepo;
        this.tagRepo = tagRepo;
        this.postRepo = postRepo;
        this.redis = redis;
    }

    public List<AdminBoardItem> listAll() {
        var boards = boardRepo.findAll();
        return boards.stream()
                .map(b -> {
                    var tags = tagRepo.findByBoardIdOrderBySortOrder(b.getId());
                    return AdminBoardItem.from(b, tags);
                })
                .toList();
    }

    @Transactional
    public AdminBoardItem create(CreateBoardRequest dto) {
        // Check duplicate name
        var existing = boardRepo.findAll().stream()
                .filter(b -> b.getName().equalsIgnoreCase(dto.name()) && !b.isDeleted())
                .findAny();
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "版块名称已存在");
        }

        // Find max sort order
        int maxSort = boardRepo.findAll().stream()
                .filter(b -> !b.isDeleted())
                .mapToInt(Board::getSortOrder)
                .max().orElse(0);

        var board = new Board();
        board.setId(UUID.randomUUID().toString());
        board.setName(dto.name());
        board.setIcon(dto.icon() != null ? dto.icon() : "");
        board.setDescription(dto.description() != null ? dto.description() : "");
        board.setSortOrder(maxSort + 1);
        board.setStatus("active");
        boardRepo.save(board);

        // Create initial tags
        if (dto.tags() != null) {
            for (int i = 0; i < dto.tags().size(); i++) {
                var tag = new Tag();
                tag.setId(UUID.randomUUID().toString());
                tag.setBoardId(board.getId());
                tag.setName(dto.tags().get(i));
                tag.setSortOrder(i);
                tagRepo.save(tag);
            }
        }

        clearBoardCache();
        var tags = tagRepo.findByBoardIdOrderBySortOrder(board.getId());
        return AdminBoardItem.from(board, tags);
    }

    @Transactional
    public void update(String id, UpdateBoardRequest dto) {
        var board = boardRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));

        if (dto.name() != null) {
            var dup = boardRepo.findAll().stream()
                    .filter(b -> b.getName().equalsIgnoreCase(dto.name()) && !b.getId().equals(id) && !b.isDeleted())
                    .findAny();
            if (dup.isPresent()) {
                throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "版块名称已存在");
            }
            board.setName(dto.name());
        }
        if (dto.icon() != null) board.setIcon(dto.icon());
        if (dto.description() != null) board.setDescription(dto.description());
        if (dto.sortOrder() != null) board.setSortOrder(dto.sortOrder());

        boardRepo.save(board);
        clearBoardCache();
    }

    @Transactional
    public void softDelete(String id) {
        var board = boardRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));

        board.setDeleted(true);
        board.setDeletedAt(java.time.Instant.now());
        board.setStatus("inactive");
        boardRepo.save(board);
        clearBoardCache();
    }

    @Transactional
    public void sort(BoardSortRequest dto) {
        for (var item : dto.items()) {
            var board = boardRepo.findById(item.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在: " + item.id()));
            board.setSortOrder(item.sortOrder());
            boardRepo.save(board);
        }
        clearBoardCache();
    }

    // ── Tag management ──────────────────────────────

    public List<AdminBoardItem.TagItem> getTags(String boardId) {
        boardRepo.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));
        return tagRepo.findByBoardIdOrderBySortOrder(boardId).stream()
                .map(AdminBoardItem.TagItem::from)
                .toList();
    }

    @Transactional
    public AdminBoardItem.TagItem addTag(String boardId, CreateTagRequest dto) {
        var board = boardRepo.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));

        // Check duplicate name within board
        var existing = tagRepo.findByBoardIdOrderBySortOrder(boardId).stream()
                .filter(t -> t.getName().equalsIgnoreCase(dto.name()))
                .findAny();
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "标签名在该版块下已存在");
        }

        int maxSort = tagRepo.findByBoardIdOrderBySortOrder(boardId).stream()
                .mapToInt(Tag::getSortOrder).max().orElse(0);

        var tag = new Tag();
        tag.setId(UUID.randomUUID().toString());
        tag.setBoardId(boardId);
        tag.setName(dto.name());
        tag.setSortOrder(maxSort + 1);
        tagRepo.save(tag);

        return AdminBoardItem.TagItem.from(tag);
    }

    @Transactional
    public void updateTag(String tagId, UpdateTagRequest dto) {
        var tag = tagRepo.findById(tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "标签不存在"));

        if (dto.name() != null) {
            var dup = tagRepo.findByBoardIdOrderBySortOrder(tag.getBoardId()).stream()
                    .filter(t -> t.getName().equalsIgnoreCase(dto.name()) && !t.getId().equals(tagId))
                    .findAny();
            if (dup.isPresent()) {
                throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "标签名在该版块下已存在");
            }
            tag.setName(dto.name());
        }
        if (dto.sortOrder() != null) tag.setSortOrder(dto.sortOrder());

        tagRepo.save(tag);
    }

    // ── Helpers ─────────────────────────────────────

    private void clearBoardCache() {
        try {
            redis.delete(BOARDS_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Failed to clear board cache: {}", e.getMessage());
        }
    }
}
