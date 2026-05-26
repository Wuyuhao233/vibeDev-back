package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.*;
import com.vibedev.service.BoardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public ApiResponse<BoardListResponse> listBoards() {
        return ApiResponse.ok(new BoardListResponse(boardService.listAll()));
    }

    @GetMapping("/{id}")
    public ApiResponse<BoardItem> getBoard(@PathVariable String id) {
        return ApiResponse.ok(boardService.getBoardDetail(id));
    }

    @GetMapping("/{id}/tags")
    public ApiResponse<List<TagItem>> getBoardTags(@PathVariable String id) {
        return ApiResponse.ok(boardService.getBoardTags(id));
    }

    @GetMapping("/{id}/posts")
    public ApiResponse<PaginatedResponse<PostCard>> getBoardPosts(
            @PathVariable String id,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "hot") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        if (limit > 50) limit = 50;
        String userId = auth != null ? auth.getPrincipal().toString() : null;
        return ApiResponse.ok(boardService.getBoardPosts(id, tag, sort, page, limit, userId));
    }
}
