package com.vibedev.controller;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock TagService tagService;
    @Mock Authentication auth;

    TagController controller;

    @BeforeEach
    void setUp() {
        controller = new TagController(tagService);
        when(auth.getPrincipal()).thenReturn("user-1");
    }

    @Test
    void follow_shouldReturnSuccess() {
        doNothing().when(tagService).followTag("user-1", "t1");

        var result = controller.follow("t1", auth);

        assertEquals(0, result.code());
        assertEquals("关注成功", result.message());
        verify(tagService).followTag("user-1", "t1");
    }

    @Test
    void unfollow_shouldReturnSuccess() {
        doNothing().when(tagService).unfollowTag("user-1", "t1");

        var result = controller.unfollow("t1", auth);

        assertEquals(0, result.code());
        assertEquals("已取消关注", result.message());
        verify(tagService).unfollowTag("user-1", "t1");
    }

    @Test
    void follow_shouldPropagateTagNotFoundError() {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "标签不存在"))
                .when(tagService).followTag("user-1", "t99");

        var ex = assertThrows(BusinessException.class,
                () -> controller.follow("t99", auth));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("标签不存在", ex.getMessage());
    }

    @Test
    void follow_shouldPropagateAlreadyFollowingError() {
        doThrow(new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "已关注该标签"))
                .when(tagService).followTag("user-1", "t1");

        var ex = assertThrows(BusinessException.class,
                () -> controller.follow("t1", auth));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
        assertEquals("已关注该标签", ex.getMessage());
    }

    @Test
    void unfollow_shouldPropagateNotFollowingError() {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "未关注该标签"))
                .when(tagService).unfollowTag("user-1", "t1");

        var ex = assertThrows(BusinessException.class,
                () -> controller.unfollow("t1", auth));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("未关注该标签", ex.getMessage());
    }
}
