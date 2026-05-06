package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.auth.*;
import com.vibedev.entity.LoginHistory;
import com.vibedev.entity.User;
import com.vibedev.entity.UserNotificationSetting;
import com.vibedev.repository.LoginHistoryRepository;
import com.vibedev.repository.UserNotificationSettingRepository;
import com.vibedev.repository.UserRepository;
import com.vibedev.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock LoginHistoryRepository loginHistoryRepo;
    @Mock UserNotificationSettingRepository notifySettingRepo;
    @Mock JwtUtil jwtUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock MailService mailService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks AuthService authService;

    private final String frontendUrl = "http://localhost:3000";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, loginHistoryRepo, notifySettingRepo,
                jwtUtil, passwordEncoder, mailService, redis, frontendUrl);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ─── Register ──────────────────────────────────────────

    @Test
    void register_shouldCreateUserAndSendEmail() {
        var req = new RegisterRequest("newuser", "new@test.com", "Pass1234");
        when(userRepo.existsByUsernameAndIsActivatedTrueAndIsDeactivatedFalse("newuser")).thenReturn(false);
        when(userRepo.existsByEmailAndIsActivatedTrueAndIsDeactivatedFalse("new@test.com")).thenReturn(false);
        when(valueOps.increment("email:limit:new@test.com:24h")).thenReturn(1L);
        when(passwordEncoder.encode("Pass1234")).thenReturn("hashed");
        when(jwtUtil.generateVerifyEmailToken(any(), eq("new@test.com"))).thenReturn("verify-token");

        assertDoesNotThrow(() -> authService.register(req, "127.0.0.1"));

        verify(userRepo).save(argThat(u -> u.getUsername().equals("newuser") && !u.isActivated()));
        verify(mailService).sendVerifyEmail(eq("new@test.com"), eq("newuser"), contains("verify-token"));
        // verify 7 notification settings initialized
        verify(notifySettingRepo, times(7)).save(any(UserNotificationSetting.class));
    }

    @Test
    void register_shouldRejectDuplicateUsername() {
        var req = new RegisterRequest("taken", "new@test.com", "Pass1234");
        when(userRepo.existsByUsernameAndIsActivatedTrueAndIsDeactivatedFalse("taken")).thenReturn(true);

        var ex = assertThrows(BusinessException.class, () -> authService.register(req, "127.0.0.1"));
        assertEquals(ErrorCode.USERNAME_TAKEN.getCode(), ex.getCode());
    }

    @Test
    void register_shouldRejectDuplicateEmail() {
        var req = new RegisterRequest("newuser", "taken@test.com", "Pass1234");
        when(userRepo.existsByUsernameAndIsActivatedTrueAndIsDeactivatedFalse("newuser")).thenReturn(false);
        when(userRepo.existsByEmailAndIsActivatedTrueAndIsDeactivatedFalse("taken@test.com")).thenReturn(true);

        var ex = assertThrows(BusinessException.class, () -> authService.register(req, "127.0.0.1"));
        assertEquals(ErrorCode.EMAIL_TAKEN.getCode(), ex.getCode());
    }

    @Test
    void register_shouldRateLimitEmail() {
        var req = new RegisterRequest("newuser", "spam@test.com", "Pass1234");
        when(userRepo.existsByUsernameAndIsActivatedTrueAndIsDeactivatedFalse("newuser")).thenReturn(false);
        when(userRepo.existsByEmailAndIsActivatedTrueAndIsDeactivatedFalse("spam@test.com")).thenReturn(false);
        when(valueOps.increment("email:limit:spam@test.com:24h")).thenReturn(4L);

        var ex = assertThrows(BusinessException.class, () -> authService.register(req, "127.0.0.1"));
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }

    // ─── Verify Email ──────────────────────────────────────

    @Test
    void verifyEmail_shouldActivateUser() {
        var userId = UUID.randomUUID();
        var user = new User();
        user.setId(userId.toString());
        user.setActivated(false);

        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("valid-token")).thenReturn(claims);
        when(jwtUtil.getTokenType(claims)).thenReturn("verify_email");
        when(jwtUtil.getUserId(claims)).thenReturn(userId);
        when(userRepo.findById(userId.toString())).thenReturn(Optional.of(user));

        authService.verifyEmail("valid-token");

        assertTrue(user.isActivated());
        verify(userRepo).save(user);
    }

    @Test
    void verifyEmail_shouldThrowOnInvalidToken() {
        when(jwtUtil.parseToken("bad-token")).thenThrow(new RuntimeException("invalid"));

        var ex = assertThrows(BusinessException.class, () -> authService.verifyEmail("bad-token"));
        assertEquals(ErrorCode.UNKNOWN.getCode(), ex.getCode());
    }

    @Test
    void verifyEmail_shouldThrowOnWrongTokenType() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("access-token")).thenReturn(claims);
        when(jwtUtil.getTokenType(claims)).thenReturn("access");

        var ex = assertThrows(BusinessException.class, () -> authService.verifyEmail("access-token"));
        assertEquals(ErrorCode.UNKNOWN.getCode(), ex.getCode());
    }

    // ─── Login ─────────────────────────────────────────────

    private User createActiveUser() {
        var user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername("testuser");
        user.setEmail("test@test.com");
        user.setPasswordHash("hashed");
        user.setNickname("Test");
        user.setRole("user");
        user.setLevel(1);
        user.setActivated(true);
        user.setTokenVersion(0);
        return user;
    }

    @Test
    void login_shouldReturnTokensOnSuccess() {
        var user = createActiveUser();
        var req = new LoginRequest("testuser", "Pass1234", true);

        when(userRepo.findByUsernameOrEmailActive("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass1234", "hashed")).thenReturn(true);
        when(jwtUtil.generateAccessToken(any(), eq("testuser"), eq("user"), eq(0)))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(), eq(0)))
                .thenReturn("refresh-token");

        var resp = authService.login(req, "127.0.0.1", "Chrome");

        assertNotNull(resp);
        assertEquals("access-token", resp.accessToken());
        assertEquals("refresh-token", resp.refreshToken());
        assertEquals(900, resp.expiresIn());
        assertEquals("testuser", resp.user().username());
        verify(loginHistoryRepo).save(any(LoginHistory.class));
    }

    @Test
    void login_shouldThrowOnWrongPassword() {
        var user = createActiveUser();
        var req = new LoginRequest("testuser", "WrongPass", false);

        when(userRepo.findByUsernameOrEmailActive("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "hashed")).thenReturn(false);
        when(valueOps.increment("login:attempt:testuser:1m")).thenReturn(1L);

        var ex = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1", "Chrome"));
        assertEquals(ErrorCode.PASSWORD_WRONG.getCode(), ex.getCode());
        assertTrue(user.getLoginFailCount() > 0);
    }

    @Test
    void login_shouldThrowWhenAccountNotFound() {
        var req = new LoginRequest("nobody", "Pass1234", false);
        when(userRepo.findByUsernameOrEmailActive("nobody")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1", "Chrome"));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void login_shouldThrowWhenNotActivated() {
        var user = createActiveUser();
        user.setActivated(false);
        var req = new LoginRequest("testuser", "Pass1234", false);

        when(userRepo.findByUsernameOrEmailActive("testuser")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1", "Chrome"));
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVATED.getCode(), ex.getCode());
    }

    // ─── Refresh Token ─────────────────────────────────────

    @Test
    void refreshToken_shouldRotateTokens() {
        var userId = UUID.randomUUID();
        var user = createActiveUser();
        user.setId(userId.toString());

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-123");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 600000));
        when(jwtUtil.parseToken("old-refresh")).thenReturn(claims);
        when(jwtUtil.getTokenType(claims)).thenReturn("refresh");
        when(jwtUtil.getUserId(claims)).thenReturn(userId);
        when(jwtUtil.getTokenVersion(claims)).thenReturn(0);
        when(redis.hasKey("refresh:blacklist:jti-123")).thenReturn(false);
        when(userRepo.findById(userId.toString())).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(eq(userId), anyString(), anyString(), eq(0)))
                .thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(eq(userId), eq(0)))
                .thenReturn("new-refresh");

        var resp = authService.refreshToken(new RefreshTokenRequest("old-refresh"));

        assertEquals("new-access", resp.accessToken());
        assertEquals("new-refresh", resp.refreshToken());
    }

    @Test
    void refreshToken_shouldThrowWhenBlacklisted() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-123");
        when(jwtUtil.parseToken("blacklisted")).thenReturn(claims);
        when(jwtUtil.getTokenType(claims)).thenReturn("refresh");
        when(redis.hasKey("refresh:blacklist:jti-123")).thenReturn(true);

        var ex = assertThrows(BusinessException.class,
                () -> authService.refreshToken(new RefreshTokenRequest("blacklisted")));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    // ─── Logout ────────────────────────────────────────────

    @Test
    void logout_shouldBlacklistToken() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-456");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 600000));
        when(jwtUtil.parseToken("to-revoke")).thenReturn(claims);

        assertDoesNotThrow(() -> authService.logout(new LogoutRequest("to-revoke")));
        verify(valueOps).set(contains("refresh:blacklist:jti-456"), eq("1"), anyLong(), any());
    }

    // ─── Forgot Password ───────────────────────────────────

    @Test
    void forgotPassword_shouldSendEmailWhenUserExists() {
        var user = createActiveUser();
        var req = new ForgotPasswordRequest("test@test.com");

        when(valueOps.increment("reset:limit:test@test.com:1h")).thenReturn(1L);
        when(userRepo.findByEmailAndIsActivatedTrueAndIsDeactivatedFalse("test@test.com"))
                .thenReturn(Optional.of(user));
        when(jwtUtil.generateResetToken(any())).thenReturn("reset-token");

        assertDoesNotThrow(() -> authService.forgotPassword(req));
        verify(mailService).sendResetPasswordEmail(eq("test@test.com"), eq("testuser"), contains("reset-token"));
    }

    @Test
    void forgotPassword_shouldNotRevealIfEmailNotExists() {
        var req = new ForgotPasswordRequest("nobody@test.com");
        when(valueOps.increment("reset:limit:nobody@test.com:1h")).thenReturn(1L);
        when(userRepo.findByEmailAndIsActivatedTrueAndIsDeactivatedFalse("nobody@test.com"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword(req));
        verify(mailService, never()).sendResetPasswordEmail(any(), any(), any());
    }

    @Test
    void forgotPassword_shouldSilentlyRateLimit() {
        var req = new ForgotPasswordRequest("test@test.com");
        when(valueOps.increment("reset:limit:test@test.com:1h")).thenReturn(2L);

        assertDoesNotThrow(() -> authService.forgotPassword(req));
        verify(userRepo, never()).findByEmailAndIsActivatedTrueAndIsDeactivatedFalse(any());
    }

    // ─── Reset Password ────────────────────────────────────

    @Test
    void resetPassword_shouldUpdatePasswordAndBumpVersion() {
        var userId = UUID.randomUUID();
        var user = createActiveUser();
        user.setId(userId.toString());
        int oldVersion = user.getTokenVersion();

        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("reset-token")).thenReturn(claims);
        when(jwtUtil.getTokenType(claims)).thenReturn("reset");
        when(jwtUtil.getUserId(claims)).thenReturn(userId);
        when(userRepo.findById(userId.toString())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hashed");

        authService.resetPassword(new ResetPasswordRequest("reset-token", "NewPass123"));

        assertEquals("new-hashed", user.getPasswordHash());
        assertEquals(oldVersion + 1, user.getTokenVersion());
        assertEquals(0, user.getLoginFailCount());
        verify(userRepo).save(user);
    }

    @Test
    void resetPassword_shouldThrowOnExpiredToken() {
        when(jwtUtil.parseToken("expired")).thenThrow(new RuntimeException("expired"));

        var ex = assertThrows(BusinessException.class,
                () -> authService.resetPassword(new ResetPasswordRequest("expired", "NewPass123")));
        assertEquals(ErrorCode.TOKEN_EXPIRED.getCode(), ex.getCode());
    }
}
