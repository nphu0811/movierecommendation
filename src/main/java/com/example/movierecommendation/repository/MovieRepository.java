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

    @Query("SELECT m FROM Movie m LEFT JOIN m.watchHistories wh GROUP BY m ORDER BY COUNT(wh) DESC")
    List<Movie> findMostWatchedMovies(Pageable pageable);

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
