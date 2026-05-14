package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Integer> {

    List<Tag> findByMovieMovieIdAndDeletedAtIsNullOrderByCreatedAtDesc(Integer movieId);

    boolean existsByUserUserIdAndMovieMovieIdAndTagAndDeletedAtIsNull(Integer userId, Integer movieId, String tag);

    default List<Tag> findByMovieMovieIdOrderByCreatedAtDesc(Integer movieId) {
        return findByMovieMovieIdAndDeletedAtIsNullOrderByCreatedAtDesc(movieId);
    }

    default boolean existsByUserUserIdAndMovieMovieIdAndTag(Integer userId, Integer movieId, String tag) {
        return existsByUserUserIdAndMovieMovieIdAndTagAndDeletedAtIsNull(userId, movieId, tag);
    }

    void deleteByTagIdAndUserUserId(Integer tagId, Integer userId);

    /**
     * Returns top tags for a movie with their counts: [tag (String), count (Long)]
     */
    @Query("SELECT t.tag, COUNT(t) FROM Tag t WHERE t.movie.movieId = :movieId AND t.deletedAt IS NULL GROUP BY t.tag ORDER BY COUNT(t) DESC")
    List<Object[]> findTopTagsByMovieId(@Param("movieId") Integer movieId);
}
