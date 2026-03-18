package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {
    List<Comment> findByMovieMovieIdOrderByCreatedAtDesc(Integer movieId);
    List<Comment> findByUserUserIdOrderByCreatedAtDesc(Integer userId);

    @Query("SELECT COUNT(c) FROM Comment c")
    Long countAllComments();
}
