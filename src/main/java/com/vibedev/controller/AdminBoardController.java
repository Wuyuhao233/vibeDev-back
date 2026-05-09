package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.admin.board.*;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.BoardManageService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminBoardController {

    private final BoardManageService boardManageService;

    public AdminBoardController(BoardManageService boardManageService) {
        this.boardManageService = boardManageService;
    }

    @GetMapping("/boards")
    public ApiResponse<List<AdminBoardItem>> listBoards() {
        return ApiResponse.ok(boardManageService.listAll());
    }

    @PostMapping("/boards")
    public ApiResponse<AdminBoardItem> create(@Valid @RequestBody CreateBoardRequest dto) {
        return ApiResponse.ok(boardManageService.create(dto));
    }

    @PutMapping("/boards/{id}")
    public ApiResponse<Void> update(@PathVariable String id, @Valid @RequestBody UpdateBoardRequest dto) {
        boardManageService.update(id, dto);
        return ApiResponse.ok();
    }

    @DeleteMapping("/boards/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        boardManageService.softDelete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/boards/sort")
    public ApiResponse<Void> sort(@Valid @RequestBody BoardSortRequest dto) {
        boardManageService.sort(dto);
        return ApiResponse.ok();
    }

    @GetMapping("/boards/{id}/tags")
    public ApiResponse<List<AdminBoardItem.TagItem>> getTags(@PathVariable String id) {
        return ApiResponse.ok(boardManageService.getTags(id));
    }

    @PostMapping("/boards/{id}/tags")
    public ApiResponse<AdminBoardItem.TagItem> addTag(
            @PathVariable String id, @Valid @RequestBody CreateTagRequest dto) {
        return ApiResponse.ok(boardManageService.addTag(id, dto));
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<Void> updateTag(@PathVariable String id, @Valid @RequestBody UpdateTagRequest dto) {
        boardManageService.updateTag(id, dto);
        return ApiResponse.ok();
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable String id) {
        boardManageService.deleteTag(id);
        return ApiResponse.ok();
    }
}
