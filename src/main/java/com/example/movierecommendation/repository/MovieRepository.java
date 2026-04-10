package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN m.genres g WHERE " +
           "LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(g.genreName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Movie> searchByTitleOrGenre(@Param("keyword") String keyword);

    @Query("SELECT m FROM Movie m LEFT JOIN m.ratings r GROUP BY m ORDER BY AVG(r.rating) DESC")
    List<Movie> findTopRatedMovies(Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.ratings r WHERE m.movieId NOT IN :excludeIds GROUP BY m ORDER BY AVG(r.rating) DESC")
    List<Movie> findTopRatedMoviesExcluding(@Param("excludeIds") List<Integer> excludeIds, Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedMovies(Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh WHERE m.movieId NOT IN :excludeIds GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedMoviesExcluding(@Param("excludeIds") List<Integer> excludeIds, Pageable pageable);

    /** Loại mọi phim user đã xem (watch_history) hoặc đã rate — tránh NOT IN list quá lớn trên PostgreSQL. */
    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh WHERE " +
           "m.movieId NOT IN (SELECT wh2.movie.movieId FROM WatchHistory wh2 WHERE wh2.user.userId = :userId) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId) " +
           "GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedMoviesExcludingUserInteractions(@Param("userId") Integer userId, Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.ratings r2 WHERE " +
           "m.movieId NOT IN (SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId) " +
           "GROUP BY m ORDER BY AVG(CAST(r2.rating AS double)) DESC")
    List<Movie> findTopRatedMoviesExcludingUserInteractions(@Param("userId") Integer userId, Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE g.genreId IN :genreIds AND " +
           "m.movieId NOT IN (SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId)")
    List<Movie> findByGenreIdsExcludingUserInteractions(@Param("genreIds") List<Integer> genreIds,
                                                        @Param("userId") Integer userId,
                                                        Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE g.genreId IN :genreIds AND m.movieId <> :targetMovieId AND " +
           "m.movieId NOT IN (SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId)")
    List<Movie> findSimilarByGenresExcludingUser(@Param("genreIds") List<Integer> genreIds,
                                               @Param("targetMovieId") Integer targetMovieId,
                                               @Param("userId") Integer userId,
                                               Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh WHERE m.movieId <> :targetMovieId AND " +
           "m.movieId NOT IN (SELECT wh2.movie.movieId FROM WatchHistory wh2 WHERE wh2.user.userId = :userId) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId) " +
           "GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedExcludingTargetAndUser(@Param("targetMovieId") Integer targetMovieId,
                                                      @Param("userId") Integer userId,
                                                      Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE g.genreId IN :genreIds AND m.movieId NOT IN :excludeIds")
    List<Movie> findByGenreIdsAndNotInIds(@Param("genreIds") List<Integer> genreIds,
                                          @Param("excludeIds") List<Integer> excludeIds,
                                          Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE g.genreId IN :genreIds AND m.movieId NOT IN :excludeIds")
    List<Movie> findByGenreIds(@Param("genreIds") List<Integer> genreIds,
                               @Param("excludeIds") List<Integer> excludeIds);

    Page<Movie> findAll(Pageable pageable);

    @Query("SELECT m FROM Movie m WHERE m.movieId NOT IN :watchedIds ORDER BY m.createdAt DESC")
    List<Movie> findNewMoviesNotWatched(@Param("watchedIds") List<Integer> watchedIds, Pageable pageable);
}
