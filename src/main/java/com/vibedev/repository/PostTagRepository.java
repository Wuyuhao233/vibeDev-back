package com.vibedev.repository;

import com.vibedev.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostTagRepository extends JpaRepository<PostTag, String> {

    List<PostTag> findByPostId(String postId);

    List<PostTag> findByPostIdIn(List<String> postIds);

    List<PostTag> findByTagIdIn(List<String> tagIds);

    List<PostTag> findByPostIdInAndTagIdIn(List<String> postIds, List<String> tagIds);
}
