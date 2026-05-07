package com.vibedev.service;

import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.moderator.AssignModeratorRequest;
import com.vibedev.dto.admin.moderator.RemoveModeratorRequest;
import com.vibedev.entity.ModeratorBoard;
import com.vibedev.entity.User;
import com.vibedev.common.BusinessException;
import com.vibedev.repository.BoardRepository;
import com.vibedev.repository.ModeratorBoardRepository;
import com.vibedev.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ModeratorService {

    private final ModeratorBoardRepository moderatorBoardRepo;
    private final UserRepository userRepo;
    private final BoardRepository boardRepo;

    public ModeratorService(ModeratorBoardRepository moderatorBoardRepo,
                            UserRepository userRepo,
                            BoardRepository boardRepo) {
        this.moderatorBoardRepo = moderatorBoardRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
    }

    @Transactional
    public void assignModerator(AssignModeratorRequest request) {
        User user = userRepo.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        if ("admin".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能将管理员降级为版主");
        }

        // Validate boards exist
        for (String boardId : request.boardIds()) {
            if (!boardRepo.existsById(boardId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "版块不存在: " + boardId);
            }
        }

        // Remove existing assignments
        moderatorBoardRepo.deleteByUserId(request.userId());

        // Create new assignments
        for (String boardId : request.boardIds()) {
            ModeratorBoard mb = new ModeratorBoard();
            mb.setId(UUID.randomUUID().toString());
            mb.setUserId(request.userId());
            mb.setBoardId(boardId);
            moderatorBoardRepo.save(mb);
        }

        // Update user role to moderator
        user.setRole(request.boardIds().isEmpty() ? "user" : "moderator");
        userRepo.save(user);
    }

    @Transactional
    public void removeModerator(RemoveModeratorRequest request) {
        User user = userRepo.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        if ("admin".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能撤销管理员的权限");
        }

        moderatorBoardRepo.deleteByUserId(request.userId());
        user.setRole("user");
        userRepo.save(user);
    }

    public List<ModeratorBoard> getModeratorBoards(String userId) {
        return moderatorBoardRepo.findByUserId(userId);
    }

    public List<String> getModeratorBoardIds(String userId) {
        return moderatorBoardRepo.findByUserId(userId).stream()
                .map(ModeratorBoard::getBoardId)
                .toList();
    }
}
