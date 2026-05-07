package com.vibedev.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class AiModerationClient {

    private static final Logger log = LoggerFactory.getLogger(AiModerationClient.class);
    private static final List<String> CATEGORIES = List.of(
            "涉政", "色情", "暴力", "垃圾广告", "辱骂攻击", "违禁品"
    );

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "涉政", List.of("政治", "政府", "抗议", "集会", "游行"),
            "色情", List.of("裸体", "性", "色情", "淫秽", "擦边"),
            "暴力", List.of("杀人", "血腥", "恐怖", "爆炸", "武器"),
            "垃圾广告", List.of("加微信", "扫码", "免费领取", "点击链接", "推广"),
            "辱骂攻击", List.of("傻逼", "白痴", "蠢货", "垃圾", "人渣"),
            "违禁品", List.of("毒品", "枪支", "假币", "赌博", "迷药")
    );

    public record AuditResult(int score, String category, String reason, boolean degraded) {}

    /**
     * Mock AI audit. In production, this would call a real cloud API.
     * Simulates content scoring based on keyword matching for realistic behavior.
     */
    public AuditResult audit(String content) {
        long start = System.currentTimeMillis();
        try {
            // Simulate API latency
            Thread.sleep(50 + ThreadLocalRandom.current().nextInt(100));

            // Check for high-risk keywords
            String lowerContent = content.toLowerCase();
            int maxScore = 0;
            String matchedCategory = null;

            for (var entry : CATEGORY_KEYWORDS.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (lowerContent.contains(keyword)) {
                        // Score based on category: abuse/violence/politics = high, spam = medium
                        int baseScore = switch (entry.getKey()) {
                            case "辱骂攻击", "暴力", "色情", "涉政" -> 70;
                            case "违禁品" -> 80;
                            default -> 40; // 垃圾广告 starts lower
                        };
                        int score = baseScore + ThreadLocalRandom.current().nextInt(0, 30);
                        if (score > maxScore) {
                            maxScore = Math.min(score, 100);
                            matchedCategory = entry.getKey();
                        }
                    }
                }
            }

            // If no keywords matched, generate low random score for normal content
            if (maxScore == 0) {
                maxScore = ThreadLocalRandom.current().nextInt(5, 45);
            }

            String category = matchedCategory != null ? matchedCategory : "normal";
            String reason = maxScore >= 85 ? "内容包含违规信息" :
                    maxScore >= 50 ? "内容疑似违规，需人工复审" : "内容正常";

            long elapsed = System.currentTimeMillis() - start;
            log.debug("AI audit completed: score={}, category={}, elapsed={}ms", maxScore, category, elapsed);

            return new AuditResult(maxScore, category, reason, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return degraded();
        } catch (Exception e) {
            log.warn("AI moderation API failed, degrading: {}", e.getMessage());
            return degraded();
        }
    }

    private AuditResult degraded() {
        return new AuditResult(-1, "unknown", "AI审核服务不可用", true);
    }
}
