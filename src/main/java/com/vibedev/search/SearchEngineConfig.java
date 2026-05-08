package com.vibedev.search;

import com.vibedev.repository.PostTagRepository;
import com.vibedev.repository.TagRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SearchEngineConfig {

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "like")
    public SearchEngine dbLikeSearchEngine(JdbcTemplate jdbcTemplate,
                                            PostTagRepository postTagRepo,
                                            TagRepository tagRepo) {
        return new DbLikeSearchEngine(jdbcTemplate, postTagRepo, tagRepo);
    }

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "fts", matchIfMissing = true)
    public SearchEngine mySqlFtsSearchEngine(JdbcTemplate jdbcTemplate,
                                              PostTagRepository postTagRepo,
                                              TagRepository tagRepo) {
        return new MySqlFtsSearchEngine(jdbcTemplate, postTagRepo, tagRepo);
    }
}
