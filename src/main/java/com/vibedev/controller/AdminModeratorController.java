package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.admin.moderator.AssignModeratorRequest;
import com.vibedev.dto.admin.moderator.RemoveModeratorRequest;
import com.vibedev.service.ModeratorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminModeratorController {

    private final ModeratorService moderatorService;

    public AdminModeratorController(ModeratorService moderatorService) {
        this.moderatorService = moderatorService;
    }

    @PutMapping("/assign-moderator")
    public ApiResponse<Void> assignModerator(@Valid @RequestBody AssignModeratorRequest request) {
        moderatorService.assignModerator(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/assign-moderator")
    public ApiResponse<Void> removeModerator(@Valid @RequestBody RemoveModeratorRequest request) {
        moderatorService.removeModerator(request);
        return ApiResponse.ok();
    }
}
