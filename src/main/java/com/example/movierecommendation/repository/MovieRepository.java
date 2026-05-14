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

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN m.genres g WHERE m.deletedAt IS NULL AND (" +
           "LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(g.genreName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Movie> searchByTitleOrGenre(@Param("keyword") String keyword);

    @Query("SELECT m FROM Movie m WHERE m.deletedAt IS NULL AND LOWER(m.title) LIKE LOWER(CONCAT(:keyword, '%')) ORDER BY m.averageRating DESC")
    List<Movie> searchByTitleOnly(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = "SELECT m.* FROM movies m " +
           "WHERE m.deleted_at IS NULL AND (" +
           "m.search_vector @@ plainto_tsquery('english', :keyword) OR " +
           "LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "EXISTS (" +
           "    SELECT 1 FROM movie_genres mg " +
           "    JOIN genres g ON g.genre_id = mg.genre_id " +
           "    WHERE mg.movie_id = m.movie_id AND LOWER(g.genre_name) LIKE LOWER(CONCAT('%', :keyword, '%'))" +
           ")) " +
           "ORDER BY ts_rank(m.search_vector, plainto_tsquery('english', :keyword)) DESC, m.average_rating DESC",
           nativeQuery = true)
    List<Movie> searchByDatabaseVector(@Param("keyword") String keyword);

    @Query("SELECT m FROM Movie m WHERE m.deletedAt IS NULL ORDER BY m.averageRating DESC, m.ratingCount DESC")
    List<Movie> findTopRatedMovies(Pageable pageable);

    @Query("SELECT m FROM Movie m WHERE m.deletedAt IS NULL AND m.movieId NOT IN :excludeIds ORDER BY m.averageRating DESC, m.ratingCount DESC")
    List<Movie> findTopRatedMoviesExcluding(@Param("excludeIds") List<Integer> excludeIds, Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh WITH wh.deletedAt IS NULL WHERE m.deletedAt IS NULL GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedMovies(Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh WITH wh.deletedAt IS NULL WHERE m.deletedAt IS NULL AND m.movieId NOT IN :excludeIds GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedMoviesExcluding(@Param("excludeIds") List<Integer> excludeIds, Pageable pageable);

    /** Loại mọi phim user đã xem (watch_history) hoặc đã rate — tránh NOT IN list quá lớn trên PostgreSQL. */
    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh WITH wh.deletedAt IS NULL WHERE m.deletedAt IS NULL AND " +
           "m.movieId NOT IN (SELECT wh2.movie.movieId FROM WatchHistory wh2 WHERE wh2.user.userId = :userId AND wh2.deletedAt IS NULL) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId AND r.deletedAt IS NULL) " +
           "GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedMoviesExcludingUserInteractions(@Param("userId") Integer userId, Pageable pageable);

    @Query("SELECT m FROM Movie m WHERE m.deletedAt IS NULL AND " +
           "m.movieId NOT IN (SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId AND wh.deletedAt IS NULL) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId AND r.deletedAt IS NULL) " +
           "ORDER BY m.averageRating DESC, m.ratingCount DESC")
    List<Movie> findTopRatedMoviesExcludingUserInteractions(@Param("userId") Integer userId, Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE m.deletedAt IS NULL AND g.genreId IN :genreIds AND " +
           "m.movieId NOT IN (SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId AND wh.deletedAt IS NULL) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId AND r.deletedAt IS NULL)")
    List<Movie> findByGenreIdsExcludingUserInteractions(@Param("genreIds") List<Integer> genreIds,
                                                        @Param("userId") Integer userId,
                                                        Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE m.deletedAt IS NULL AND g.genreId IN :genreIds AND m.movieId <> :targetMovieId AND " +
           "m.movieId NOT IN (SELECT wh.movie.movieId FROM WatchHistory wh WHERE wh.user.userId = :userId AND wh.deletedAt IS NULL) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId AND r.deletedAt IS NULL)")
    List<Movie> findSimilarByGenresExcludingUser(@Param("genreIds") List<Integer> genreIds,
                                               @Param("targetMovieId") Integer targetMovieId,
                                               @Param("userId") Integer userId,
                                               Pageable pageable);

    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh WITH wh.deletedAt IS NULL WHERE m.deletedAt IS NULL AND m.movieId <> :targetMovieId AND " +
           "m.movieId NOT IN (SELECT wh2.movie.movieId FROM WatchHistory wh2 WHERE wh2.user.userId = :userId AND wh2.deletedAt IS NULL) AND " +
           "m.movieId NOT IN (SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId AND r.deletedAt IS NULL) " +
           "GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedExcludingTargetAndUser(@Param("targetMovieId") Integer targetMovieId,
                                                      @Param("userId") Integer userId,
                                                      Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE m.deletedAt IS NULL AND g.genreId IN :genreIds AND m.movieId NOT IN :excludeIds")
    List<Movie> findByGenreIdsAndNotInIds(@Param("genreIds") List<Integer> genreIds,
                                          @Param("excludeIds") List<Integer> excludeIds,
                                          Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m JOIN m.genres g WHERE m.deletedAt IS NULL AND g.genreId IN :genreIds AND m.movieId NOT IN :excludeIds")
    List<Movie> findByGenreIds(@Param("genreIds") List<Integer> genreIds,
                               @Param("excludeIds") List<Integer> excludeIds);

    Page<Movie> findByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT m FROM Movie m WHERE m.deletedAt IS NULL AND m.movieId NOT IN :watchedIds ORDER BY m.createdAt DESC")
    List<Movie> findNewMoviesNotWatched(@Param("watchedIds") List<Integer> watchedIds, Pageable pageable);

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.genres WHERE m.deletedAt IS NULL AND m.movieId IN :ids")
    List<Movie> findAllByIdWithGenres(@Param("ids") List<Integer> ids);

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.genres WHERE m.deletedAt IS NULL")
    List<Movie> findAllWithGenres();
}
