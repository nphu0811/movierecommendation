package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.WatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Integer> {

    List<WatchHistory> findByUserUserIdAndDeletedAtIsNullOrderByWatchedAtAsc(Integer userId);
    List<WatchHistory> findByUserUserIdAndDeletedAtIsNullOrderByWatchedAtDesc(Integer userId);

    List<WatchHistory> findByUserUserIdAndDeletedAtIsNullOrderByWatchedAtAsc(Integer userId, Pageable pageable);

    default List<WatchHistory> findByUserUserIdOrderByWatchedAtAsc(Integer userId) {
        return findByUserUserIdAndDeletedAtIsNullOrderByWatchedAtAsc(userId);
    }

    default List<WatchHistory> findByUserUserIdOrderByWatchedAtDesc(Integer userId) {
        return findByUserUserIdAndDeletedAtIsNullOrderByWatchedAtDesc(userId);
    }

    default List<WatchHistory> findByUserUserIdOrderByWatchedAtAsc(Integer userId, Pageable pageable) {
        return findByUserUserIdAndDeletedAtIsNullOrderByWatchedAtAsc(userId, pageable);
    }

    @Query("SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId AND wh.deletedAt IS NULL")
    List<Integer> findWatchedMovieIdsByUserId(@Param("userId") Integer userId);

    Optional<WatchHistory> findByUserUserIdAndMovieMovieIdAndDeletedAtIsNull(Integer userId, Integer movieId);

    boolean existsByUserUserIdAndMovieMovieIdAndDeletedAtIsNull(Integer userId, Integer movieId);

    default Optional<WatchHistory> findByUserUserIdAndMovieMovieId(Integer userId, Integer movieId) {
        return findByUserUserIdAndMovieMovieIdAndDeletedAtIsNull(userId, movieId);
    }

    default boolean existsByUserUserIdAndMovieMovieId(Integer userId, Integer movieId) {
        return existsByUserUserIdAndMovieMovieIdAndDeletedAtIsNull(userId, movieId);
    }

    @Query("SELECT COUNT(wh) FROM WatchHistory wh WHERE wh.movie.movieId = :movieId AND wh.deletedAt IS NULL")
    Long countByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(DISTINCT wh.user.userId) FROM WatchHistory wh WHERE wh.deletedAt IS NULL")
    Long countActiveUsers();
}
