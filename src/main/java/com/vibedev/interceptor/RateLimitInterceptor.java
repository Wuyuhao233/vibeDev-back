package com.vibedev.interceptor;

import com.vibedev.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final int DEFAULT_RATE_LIMIT = 60;
    private static final int DEFAULT_WINDOW_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler) throws Exception {
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String key = "rate_limit:" + clientIp + ":" + method + ":" + uri;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null) {
            if (count == 1) {
                redisTemplate.expire(key, DEFAULT_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count > DEFAULT_RATE_LIMIT) {
                response.setStatus(200);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"code\":" + ErrorCode.RATE_LIMITED.getCode()
                        + ",\"message\":\"Too many requests\",\"data\":null}");
                log.warn("Rate limit exceeded for IP: {} on {}", clientIp, uri);
                return false;
            }
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
