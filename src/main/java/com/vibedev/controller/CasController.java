package com.vibedev.controller;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.User;
import com.vibedev.repository.UserRepository;
import com.vibedev.service.CasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Mock CAS controller that simulates a CAS single sign-on server.
 * Provides a simple email+password login page for testing/development.
 */
@RestController
@RequestMapping("/api/v1/auth/cas")
public class CasController {

    private static final Logger log = LoggerFactory.getLogger(CasController.class);

    private static final String TICKET_PREFIX = "ST-mock-";
    private static final String TICKET_KEY_PREFIX = "cas:ticket:";
    private static final int TICKET_TTL_SECONDS = 30;

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    public CasController(UserRepository userRepo, PasswordEncoder passwordEncoder,
                         StringRedisTemplate redis) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    /**
     * GET /api/v1/auth/cas/authorize?redirect=xxx
     * Returns a mock CAS login HTML page.
     */
    @GetMapping("/authorize")
    public ResponseEntity<String> authorizePage(@RequestParam String redirect) {
        String html = buildLoginPage(redirect);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * POST /api/v1/auth/cas/authorize
     * Validates email+password, generates a mock service ticket, and redirects back.
     */
    @PostMapping("/authorize")
    public ResponseEntity<Void> authorizeSubmit(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String redirect) {

        // Find user by email
        var user = userRepo.findByEmailAndIsActivatedTrueAndIsDeactivatedFalse(email.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "邮箱未注册或账号不可用"));

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG, "密码错误");
        }

        // Generate mock service ticket
        String ticket = TICKET_PREFIX + UUID.randomUUID().toString();

        // Build CAS attributes JSON and store in Redis
        String casId = user.getCasId() != null ? user.getCasId() : "cas-" + user.getId();
        String json = String.format(
                "{\"casId\":\"%s\",\"username\":\"%s\",\"email\":\"%s\"}",
                escapeJson(casId), escapeJson(user.getUsername()), escapeJson(user.getEmail()));
        redis.opsForValue().set(TICKET_KEY_PREFIX + ticket, json, Duration.ofSeconds(TICKET_TTL_SECONDS));

        log.info("Mock CAS ticket generated: {} for user: {}", ticket, user.getEmail());

        // Build redirect URL: {redirect}?ticket={ticket}&service={service}
        String serviceUrl = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
        String location = redirect + "?ticket=" + ticket + "&service=" + serviceUrl;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    private String buildLoginPage(String redirect) {
        String escapedRedirect = escapeHtml(redirect);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>CAS 统一认证 - VibeDev</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans SC", sans-serif;
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                        }
                        .cas-card {
                            background: #fff;
                            border-radius: 12px;
                            padding: 40px;
                            width: 400px;
                            max-width: 90vw;
                            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        }
                        .cas-logo {
                            text-align: center;
                            margin-bottom: 24px;
                        }
                        .cas-logo h1 {
                            font-size: 24px;
                            color: #333;
                            font-weight: 700;
                        }
                        .cas-logo p {
                            font-size: 13px;
                            color: #888;
                            margin-top: 4px;
                        }
                        .cas-error {
                            background: #fef2f2;
                            border: 1px solid #fecaca;
                            border-radius: 8px;
                            padding: 10px 14px;
                            margin-bottom: 16px;
                            color: #dc2626;
                            font-size: 13px;
                            display: none;
                        }
                        .cas-field {
                            margin-bottom: 16px;
                        }
                        .cas-field label {
                            display: block;
                            font-size: 13px;
                            font-weight: 500;
                            color: #374151;
                            margin-bottom: 6px;
                        }
                        .cas-field input {
                            width: 100%%;
                            padding: 10px 12px;
                            border: 1px solid #d1d5db;
                            border-radius: 8px;
                            font-size: 14px;
                            transition: border-color 0.2s;
                            outline: none;
                        }
                        .cas-field input:focus {
                            border-color: #667eea;
                            box-shadow: 0 0 0 3px rgba(102,126,234,0.15);
                        }
                        .cas-btn {
                            width: 100%%;
                            padding: 12px;
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            color: #fff;
                            border: none;
                            border-radius: 8px;
                            font-size: 15px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: opacity 0.2s, transform 0.1s;
                        }
                        .cas-btn:hover { opacity: 0.92; }
                        .cas-btn:active { transform: scale(0.98); }
                        .cas-btn:disabled { opacity: 0.5; cursor: not-allowed; }
                        .cas-back {
                            text-align: center;
                            margin-top: 16px;
                        }
                        .cas-back a {
                            color: #667eea;
                            font-size: 13px;
                            text-decoration: none;
                        }
                        .cas-back a:hover { text-decoration: underline; }
                    </style>
                </head>
                <body>
                    <div class="cas-card">
                        <div class="cas-logo">
                            <h1>🔐 CAS 统一认证</h1>
                        </div>
                        <div class="cas-error" id="error"></div>
                        <form id="casForm" method="POST" action="/api/v1/auth/cas/authorize">
                            <input type="hidden" name="redirect" value="%s" />
                            <div class="cas-field">
                                <label for="email">邮箱</label>
                                <input type="email" id="email" name="email"
                                       placeholder="请输入邮箱" required autofocus />
                            </div>
                            <div class="cas-field">
                                <label for="password">密码</label>
                                <input type="password" id="password" name="password"
                                       placeholder="请输入密码" required />
                            </div>
                            <button type="submit" class="cas-btn" id="submitBtn">登录</button>
                        </form>
                        <div class="cas-back">
                            <a href="%s">← 返回 VibeDev</a>
                        </div>
                    </div>
                    <script>
                        const form = document.getElementById('casForm');
                        const errorEl = document.getElementById('error');
                        const submitBtn = document.getElementById('submitBtn');

                        form.addEventListener('submit', async (e) => {
                            e.preventDefault();
                            errorEl.style.display = 'none';
                            submitBtn.disabled = true;
                            submitBtn.textContent = '验证中...';

                            const formData = new FormData(form);
                            const params = new URLSearchParams(formData);

                            try {
                                const resp = await fetch('/api/v1/auth/cas/authorize', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                    body: params.toString()
                                });

                                if (resp.redirected) {
                                    window.location.href = resp.url;
                                    return;
                                }

                                if (!resp.ok) {
                                    const text = await resp.text();
                                    let msg = '登录失败，请检查邮箱和密码';
                                    try {
                                        const json = JSON.parse(text);
                                        msg = json.message || msg;
                                    } catch (_) {}
                                    throw new Error(msg);
                                }
                            } catch (err) {
                                errorEl.textContent = err.message;
                                errorEl.style.display = 'block';
                                submitBtn.disabled = false;
                                submitBtn.textContent = '登录';
                            }
                        });
                    </script>
                </body>
                </html>
                """.formatted(escapedRedirect, escapedRedirect);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
