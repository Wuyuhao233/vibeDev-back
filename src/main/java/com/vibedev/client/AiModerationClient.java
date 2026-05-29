package com.vibedev.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek-based AI content moderation client.
 * Calls the DeepSeek Chat Completions API with a structured moderation prompt,
 * then parses the JSON response into risk score / category / reason.
 */
@Component
public class AiModerationClient {

    private static final Logger log = LoggerFactory.getLogger(AiModerationClient.class);

    private static final String SYSTEM_PROMPT = """
            你是一个专业的内容安全审核助手，负责审核用户生成的内容。
            请对以下内容进行审核，判断是否包含违规信息。
            
            违规类别包括：涉政、色情、暴力、垃圾广告、辱骂攻击、违禁品、其他违规。
            
            请严格按照以下JSON格式返回审核结果，不要输出任何其他内容：
           {"score": <0-100的整数风险分数>, "category": "<违规类别>", "reason": "<简要原因>", "snippet": "<原文中的违规片段，最多80字>"}
           
           评分标准：
           - 0-25：完全正常的内容
           - 26-49：轻微可疑，但基本正常
           - 50-74：疑似违规，需人工复审
           - 75-100：明确违规，应自动拦截
           
           如果内容正常，category 填 "normal"，score 填较低的分数（如10-30），reason 填 "内容正常"，snippet 填空字符串。
           如果内容违规，snippet 必须从原文中截取具体的违规文字片段（最多80字），便于用户了解问题所在。
           """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public AiModerationClient(
            @Value("${ai.moderation.api-key}") String apiKey,
            @Value("${ai.moderation.model}") String model,
            @Value("${ai.moderation.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    public record AuditResult(int score, String category, String reason, String snippet, boolean degraded) {}

    /**
     * Audit content via DeepSeek API.
     * On any failure (network, parse error, etc.), returns a degraded result.
     */
    public AuditResult audit(String content) {
        if (content == null || content.isBlank()) {
            return new AuditResult(0, "normal", "内容为空", "", false);
        }

        long start = System.currentTimeMillis();
        try {
            // Truncate content to avoid excessive token cost (max ~2000 chars)
            String truncated = content.length() > 2000 ? content.substring(0, 2000) : content;

            var requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "请审核以下内容：\n\n" + truncated)
                    ),
                    "temperature", 0.1,
                    "max_tokens", 512
            );

            String response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(s -> s != HttpStatus.OK, (req, resp) -> {
                        throw new RuntimeException("DeepSeek API returned " + resp.getStatusCode());
                    })
                    .body(String.class);

            long elapsed = System.currentTimeMillis() - start;
            log.debug("DeepSeek API response in {}ms: {}", elapsed, response);

            return parseResponse(response);

        } catch (Exception e) {
            log.warn("AI moderation API call failed, degrading to manual review: {}", e.getMessage());
            return degraded();
        }
    }

    @SuppressWarnings("unchecked")
    private AuditResult parseResponse(String responseBody) {
        try {
            var root = objectMapper.readValue(responseBody, Map.class);
            var choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("DeepSeek response has no choices: {}", responseBody);
                return degraded();
            }
            var message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                log.warn("DeepSeek response has no message: {}", responseBody);
                return degraded();
            }
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                log.warn("DeepSeek response has empty content");
                return degraded();
            }

            // Extract JSON from the response (may be wrapped in markdown code fences)
            String json = extractJson(content);
            var result = objectMapper.readValue(json, Map.class);

            int score = toInt(result.get("score"), 50);
            String category = toString(result.get("category"), "unknown");
            String reason = toString(result.get("reason"), "AI审核结果");
            String snippet = toString(result.get("snippet"), "");

            // Clamp score to 0-100
            score = Math.max(0, Math.min(100, score));

            log.debug("AI audit parsed: score={}, category={}, reason={}, snippet={}", score, category, reason, snippet);
            return new AuditResult(score, category, reason, snippet, false);

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse DeepSeek JSON response: {}", e.getMessage());
            return degraded();
        } catch (Exception e) {
            log.warn("Unexpected error parsing AI response: {}", e.getMessage());
            return degraded();
        }
    }

    /**
     * Extract JSON object from text that may contain markdown fences or extra text.
     */
    private String extractJson(String text) {
        String trimmed = text.trim();
        // Remove markdown code fences: ```json ... ```
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        // Find the first { and last }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return trimmed;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private String toString(Object value, String defaultValue) {
        return value != null ? value.toString() : defaultValue;
    }

    private AuditResult degraded() {
        return new AuditResult(-1, "unknown", "AI审核服务不可用", "", true);
    }
}
