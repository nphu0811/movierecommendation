package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.WatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Integer> {

    List<WatchHistory> findByUserUserIdOrderByWatchedAtDesc(Integer userId);

    List<WatchHistory> findByUserUserIdOrderByWatchedAtDesc(Integer userId, Pageable pageable);

    @Query("SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId")
    List<Integer> findWatchedMovieIdsByUserId(@Param("userId") Integer userId);

    boolean existsByUserUserIdAndMovieMovieId(Integer userId, Integer movieId);

    @Query("SELECT COUNT(wh) FROM WatchHistory wh WHERE wh.movie.movieId = :movieId")
    Long countByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(DISTINCT wh.user.userId) FROM WatchHistory wh")
    Long countActiveUsers();
}
