package com.vibedev.task;

import com.vibedev.entity.Post;
import com.vibedev.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HotScoreTask {

    private static final Logger log = LoggerFactory.getLogger(HotScoreTask.class);
    private static final Pattern IMG_PATTERN = Pattern.compile("!\\[.*?]\\((https?://\\S+)\\)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```");
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("[*#>`~|_\\-]");

    private final PostRepository postRepo;

    public HotScoreTask(PostRepository postRepo) {
        this.postRepo = postRepo;
    }

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void updateHeatScores() {
        log.info("HotScoreTask started");

        // Update heat_score for all posts within 7 days
        int updated = postRepo.updateHeatScores();
        log.info("Updated heat_score for {} posts", updated);

        // Update has_code_block, cover_image_url, content_length for posts missing these
        Instant since = Instant.now().minusSeconds(7 * 86400);
        int batchSize = 500;
        int page = 0;

        while (true) {
            var posts = postRepo.findAll(PageRequest.of(page, batchSize)).getContent();
            if (posts.isEmpty()) break;

            int enriched = 0;
            for (Post post : posts) {
                boolean changed = false;

                if (post.getContentMarkdown() != null) {
                    // has_code_block
                    if (!post.isHasCodeBlock()) {
                        Matcher m = CODE_BLOCK_PATTERN.matcher(post.getContentMarkdown());
                        int count = 0;
                        while (m.find()) count++;
                        if (count >= 2) {
                            post.setHasCodeBlock(true);
                            changed = true;
                        }
                    }

                    // cover_image_url
                    if (post.getCoverImageUrl() == null || post.getCoverImageUrl().isBlank()) {
                        Matcher m = IMG_PATTERN.matcher(post.getContentMarkdown());
                        if (m.find()) {
                            post.setCoverImageUrl(m.group(1));
                            changed = true;
                        }
                    }

                    // content_length
                    if (post.getContentLength() == 0) {
                        String plain = MARKDOWN_PATTERN.matcher(post.getContentMarkdown())
                                .replaceAll("");
                        post.setContentLength(plain.length());
                        changed = true;
                    }
                }

                if (changed) {
                    postRepo.save(post);
                    enriched++;
                }
            }

            if (enriched > 0) {
                log.debug("Enriched {} posts on page {}", enriched, page);
            }

            if (posts.size() < batchSize) break;
            page++;
        }

        log.info("HotScoreTask completed");
    }
}
