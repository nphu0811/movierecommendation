package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Integer> {

    Optional<Rating> findByUserUserIdAndMovieMovieId(Integer userId, Integer movieId);
    List<Rating> findByUserUserId(Integer userId);

    @Query("SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId")
    List<Integer> findRatedMovieIdsByUserId(@Param("userId") Integer userId);
    List<Rating> findByMovieMovieId(Integer movieId);

    // Chỉ lấy users đã rate ít nhất 1 phim trong danh sách (thay thế findAll)
    @Query("SELECT DISTINCT r.user.userId FROM Rating r WHERE r.movie.movieId IN :movieIds AND r.user.userId != :userId")
    List<Integer> findUserIdsWithCommonMovies(@Param("movieIds") List<Integer> movieIds,
                                               @Param("userId") Integer userId);

    // Lấy ratings của danh sách users cụ thể
    @Query("SELECT r FROM Rating r JOIN FETCH r.movie JOIN FETCH r.user WHERE r.user.userId IN :userIds")
    List<Rating> findByUserUserIdIn(@Param("userIds") List<Integer> userIds);

    @Query("SELECT r.movie.movieId, AVG(CAST(r.rating AS double)), COUNT(r) FROM Rating r WHERE r.movie.movieId IN :movieIds GROUP BY r.movie.movieId")
    List<Object[]> findRatingStatsByMovieIds(@Param("movieIds") List<Integer> movieIds);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Rating r WHERE r.movie.movieId = :movieId")
    Double findAverageRatingByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.movie.movieId = :movieId")
    Long countByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(r) FROM Rating r")
    long countAllRatings();
}
