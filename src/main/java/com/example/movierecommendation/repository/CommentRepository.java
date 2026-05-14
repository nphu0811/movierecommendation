package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {
    List<Comment> findByMovieMovieIdAndDeletedAtIsNullOrderByCreatedAtDesc(Integer movieId);
    List<Comment> findByUserUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Integer userId);

    default List<Comment> findByMovieMovieIdOrderByCreatedAtDesc(Integer movieId) {
        return findByMovieMovieIdAndDeletedAtIsNullOrderByCreatedAtDesc(movieId);
    }

    default List<Comment> findByUserUserIdOrderByCreatedAtDesc(Integer userId) {
        return findByUserUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
    }

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.deletedAt IS NULL")
    Long countAllComments();
}
