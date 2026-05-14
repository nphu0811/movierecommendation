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

    Optional<Rating> findByUserUserIdAndMovieMovieIdAndDeletedAtIsNull(Integer userId, Integer movieId);
    List<Rating> findByUserUserIdAndDeletedAtIsNull(Integer userId);

    default Optional<Rating> findByUserUserIdAndMovieMovieId(Integer userId, Integer movieId) {
        return findByUserUserIdAndMovieMovieIdAndDeletedAtIsNull(userId, movieId);
    }

    default List<Rating> findByUserUserId(Integer userId) {
        return findByUserUserIdAndDeletedAtIsNull(userId);
    }

    @Query("SELECT r.movie.movieId FROM Rating r WHERE r.user.userId = :userId AND r.deletedAt IS NULL")
    List<Integer> findRatedMovieIdsByUserId(@Param("userId") Integer userId);
    List<Rating> findByMovieMovieIdAndDeletedAtIsNull(Integer movieId);

    default List<Rating> findByMovieMovieId(Integer movieId) {
        return findByMovieMovieIdAndDeletedAtIsNull(movieId);
    }

    // Chỉ lấy users đã rate ít nhất 1 phim trong danh sách (thay thế findAll)
    @Query("SELECT DISTINCT r.user.userId FROM Rating r WHERE r.movie.movieId IN :movieIds AND r.user.userId != :userId AND r.deletedAt IS NULL")
    List<Integer> findUserIdsWithCommonMovies(@Param("movieIds") List<Integer> movieIds,
                                               @Param("userId") Integer userId);

    // Lấy ratings của danh sách users cụ thể
    @Query("SELECT r FROM Rating r JOIN FETCH r.movie JOIN FETCH r.user WHERE r.user.userId IN :userIds AND r.deletedAt IS NULL")
    List<Rating> findByUserUserIdIn(@Param("userIds") List<Integer> userIds);

    @Query("SELECT r.movie.movieId, AVG(CAST(r.rating AS double)), COUNT(r) FROM Rating r WHERE r.movie.movieId IN :movieIds AND r.deletedAt IS NULL GROUP BY r.movie.movieId")
    List<Object[]> findRatingStatsByMovieIds(@Param("movieIds") List<Integer> movieIds);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Rating r WHERE r.movie.movieId = :movieId AND r.deletedAt IS NULL")
    Double findAverageRatingByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.movie.movieId = :movieId AND r.deletedAt IS NULL")
    Long countByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.deletedAt IS NULL")
    long countAllRatings();
}
