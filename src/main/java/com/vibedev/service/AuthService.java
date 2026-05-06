package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.auth.*;
import com.vibedev.dto.user.UserSummary;
import com.vibedev.entity.LoginHistory;
import com.vibedev.entity.User;
import com.vibedev.entity.UserNotificationSetting;
import com.vibedev.repository.LoginHistoryRepository;
import com.vibedev.repository.UserNotificationSettingRepository;
import com.vibedev.repository.UserRepository;
import com.vibedev.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final LoginHistoryRepository loginHistoryRepo;
    private final UserNotificationSettingRepository notifySettingRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final StringRedisTemplate redis;

    private final String frontendUrl;

    public AuthService(UserRepository userRepo, LoginHistoryRepository loginHistoryRepo,
                       UserNotificationSettingRepository notifySettingRepo,
                       JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
                       MailService mailService, StringRedisTemplate redis,
                       @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.userRepo = userRepo;
        this.loginHistoryRepo = loginHistoryRepo;
        this.notifySettingRepo = notifySettingRepo;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.redis = redis;
        this.frontendUrl = frontendUrl;
    }

    // ─── Register ─────────────────────────────────────────

    @Transactional
    public void register(RegisterRequest req, String clientIp) {
        // check username taken
        if (userRepo.existsByUsernameAndIsActivatedTrueAndIsDeactivatedFalse(req.username())) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN, "用户名已被占用");
        }
        // check email taken
        if (userRepo.existsByEmailAndIsActivatedTrueAndIsDeactivatedFalse(req.email())) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN, "该邮箱已被注册");
        }

        // rate limit: 24h max 3 emails
        String rateKey = "email:limit:" + req.email() + ":24h";
        Long count = redis.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redis.expire(rateKey, 24, TimeUnit.HOURS);
        }
        if (count != null && count > 3) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "发送过于频繁，请24小时后再试");
        }

        // remove unactivated old record with same email/username
        userRepo.findByEmail(req.email()).ifPresent(u -> {
            if (!u.isActivated()) userRepo.delete(u);
        });
        userRepo.findByUsername(req.username()).ifPresent(u -> {
            if (!u.isActivated()) userRepo.delete(u);
        });

        // create user
        var user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.username());
        user.setRole("user");
        user.setLevel(1);
        user.setPoints(0);
        user.setActivated(false);
        user.setTokenVersion(0);
        userRepo.save(user);

        // initialize 7 default notification settings
        List<String> eventTypes = List.of(
                "post_replied", "reply_quoted", "received_like", "post_collected",
                "post_essenced", "post_pinned", "user_banned");
        for (String et : eventTypes) {
            var setting = new UserNotificationSetting(
                    UUID.randomUUID().toString(), user.getId(), et, "site");
            notifySettingRepo.save(setting);
        }

        // send verify email
        String token = jwtUtil.generateVerifyEmailToken(UUID.fromString(user.getId()), user.getEmail());
        String verifyUrl = frontendUrl + "/verify-email?token=" + token;
        mailService.sendVerifyEmail(user.getEmail(), user.getUsername(), verifyUrl);

        log.info("User registered: {} ({})", user.getUsername(), user.getId());
    }

    // ─── Verify Email ─────────────────────────────────────

    @Transactional
    public void verifyEmail(String token) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNKNOWN, "验证链接无效");
        }
        if (!"verify_email".equals(jwtUtil.getTokenType(claims))) {
            throw new BusinessException(ErrorCode.UNKNOWN, "验证链接无效");
        }
        UUID userId = jwtUtil.getUserId(claims);

        var user = userRepo.findById(userId.toString())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "验证链接无效"));

        if (user.isActivated()) {
            return; // already activated
        }

        user.setActivated(true);
        userRepo.save(user);
        log.info("Email verified for user: {}", user.getId());
    }

    // ─── Login ────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest req, String ip, String userAgent) {
        String account = req.usernameOrEmail().trim();

        // check Redis lock
        String lockKey = "login:locked:" + account;
        String lockedUntil = redis.opsForValue().get(lockKey);
        if (lockedUntil != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请稍后再试");
        }

        var user = userRepo.findByUsernameOrEmailActive(account)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在"));

        if (!user.isActivated()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED, "账号未激活，请先验证邮箱");
        }

        // DB-level lock check (fallback)
        if (user.getLoginLockedUntil() != null && user.getLoginLockedUntil().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后再试");
        }

        // verify password
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            handleLoginFail(user, account);
            // record failed login history
            saveLoginHistory(user.getId(), "email", ip, userAgent, false);
            int remainAttempts = 5 - user.getLoginFailCount();
            throw new BusinessException(ErrorCode.PASSWORD_WRONG,
                    "密码错误，还剩 " + Math.max(0, remainAttempts) + " 次机会");
        }

        // success: clear fail counters
        user.setLoginFailCount(0);
        user.setLoginLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        // clear Redis rate limit keys
        redis.delete("login:attempt:" + account + ":1m");
        redis.delete(lockKey);

        // record login history
        saveLoginHistory(user.getId(), "email", ip, userAgent, true);

        // generate tokens
        UUID userId = UUID.fromString(user.getId());
        String accessToken = jwtUtil.generateAccessToken(userId, user.getUsername(), user.getRole(), user.getTokenVersion());
        String refreshToken = jwtUtil.generateRefreshToken(userId, user.getTokenVersion());

        log.info("User logged in: {}", user.getUsername());
        return new LoginResponse(accessToken, refreshToken, 900, UserSummary.from(user));
    }

    private void handleLoginFail(User user, String account) {
        // Redis attempt counter
        String attemptKey = "login:attempt:" + account + ":1m";
        Long attempts = redis.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1) {
            redis.expire(attemptKey, 60, TimeUnit.SECONDS);
        }

        // DB fallback
        user.setLoginFailCount(user.getLoginFailCount() + 1);

        if ((attempts != null && attempts >= 5) || user.getLoginFailCount() >= 5) {
            Instant lockUntil = Instant.now().plusSeconds(900);
            user.setLoginLockedUntil(lockUntil);
            redis.opsForValue().set("login:locked:" + account, lockUntil.toString(), 900, TimeUnit.SECONDS);
        }
        userRepo.save(user);
    }

    // ─── Refresh Token ────────────────────────────────────

    public RefreshTokenResponse refreshToken(RefreshTokenRequest req) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(req.refreshToken());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token 已过期");
        }
        if (!"refresh".equals(jwtUtil.getTokenType(claims))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无效的 Token 类型");
        }

        UUID userId = jwtUtil.getUserId(claims);
        int tokenVersion = jwtUtil.getTokenVersion(claims);

        // check blacklist
        String blacklistKey = "refresh:blacklist:" + claims.getId();
        if (Boolean.TRUE.equals(redis.hasKey(blacklistKey))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 已失效");
        }

        var user = userRepo.findById(userId.toString())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));

        if (user.getTokenVersion() != tokenVersion) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 已失效");
        }

        // blacklist old refresh token
        long remainingTtl = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingTtl > 0) {
            redis.opsForValue().set(blacklistKey, "1", remainingTtl, TimeUnit.MILLISECONDS);
        }

        // generate new pair
        String accessToken = jwtUtil.generateAccessToken(userId, user.getUsername(), user.getRole(), user.getTokenVersion());
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, user.getTokenVersion());

        return new RefreshTokenResponse(accessToken, newRefreshToken, 900);
    }

    // ─── Logout ───────────────────────────────────────────

    public void logout(LogoutRequest req) {
        try {
            Claims claims = jwtUtil.parseToken(req.refreshToken());
            long remainingTtl = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingTtl > 0) {
                redis.opsForValue().set("refresh:blacklist:" + claims.getId(), "1",
                        remainingTtl, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {
            // token already invalid, logout is still successful
        }
    }

    // ─── Forgot Password ──────────────────────────────────

    public void forgotPassword(ForgotPasswordRequest req) {
        // rate limit: 1h max 1
        String rateKey = "reset:limit:" + req.email() + ":1h";
        Long count = redis.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redis.expire(rateKey, 1, TimeUnit.HOURS);
        }
        if (count != null && count > 1) {
            // silently return, don't reveal if email exists
            return;
        }

        var userOpt = userRepo.findByEmailAndIsActivatedTrueAndIsDeactivatedFalse(req.email());
        if (userOpt.isEmpty()) {
            return; // silently return
        }

        var user = userOpt.get();
        String token = jwtUtil.generateResetToken(UUID.fromString(user.getId()));
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        mailService.sendResetPasswordEmail(user.getEmail(), user.getUsername(), resetUrl);
    }

    // ─── Reset Password ───────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(req.token());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "重置链接已失效");
        }
        if (!"reset".equals(jwtUtil.getTokenType(claims))) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "重置链接已失效");
        }

        UUID userId = jwtUtil.getUserId(claims);
        var user = userRepo.findById(userId.toString())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_EXPIRED, "重置链接已失效"));

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setLoginFailCount(0);
        user.setLoginLockedUntil(null);
        userRepo.save(user);

        log.info("Password reset for user: {}", user.getId());
    }

    // ─── helpers ──────────────────────────────────────────

    private void saveLoginHistory(String userId, String loginType, String ip, String userAgent, boolean success) {
        var lh = new LoginHistory(UUID.randomUUID().toString(), userId, loginType, ip,
                userAgent != null ? userAgent : "", success);
        loginHistoryRepo.save(lh);
    }
}
