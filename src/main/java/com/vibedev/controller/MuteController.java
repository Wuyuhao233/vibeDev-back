package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.mute.MuteRecordItem;
import com.vibedev.dto.mute.MuteRequest;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.MuteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class MuteController {

    private final MuteService muteService;

    public MuteController(MuteService muteService) {
        this.muteService = muteService;
    }

    @PostMapping("/users/{id}/ban")
    public ApiResponse<Void> ban(@PathVariable String id, @RequestBody MuteRequest dto,
                                  Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        muteService.muteUser(userId, role, id, dto.boardId(), dto.duration(), dto.reason());
        return ApiResponse.ok();
    }

    @DeleteMapping("/users/{id}/ban")
    public ApiResponse<Void> unban(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        muteService.unmuteUser(userId, id);
        return ApiResponse.ok();
    }

    @GetMapping("/users/{id}/mute-history")
    public ApiResponse<List<MuteRecordItem>> history(@PathVariable String id) {
        return ApiResponse.ok(muteService.getHistory(id));
    }
}
