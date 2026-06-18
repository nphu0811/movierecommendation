package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.UserRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, Integer> {
    List<UserRecommendation> findByUserUserIdAndAlgorithmTypeOrderByScoreDescGeneratedAtDesc(
        Integer userId, String algorithmType, Pageable pageable);
    void deleteByUserUserIdAndAlgorithmType(Integer userId, String algorithmType);

    @Query("SELECT ur.movie.title, COUNT(ur) as c FROM UserRecommendation ur GROUP BY ur.movie.title ORDER BY c DESC")
    List<Object[]> findTopRecommendedMovies(Pageable pageable);

    @Query("SELECT ur.algorithmType, COUNT(ur) as c FROM UserRecommendation ur GROUP BY ur.algorithmType ORDER BY c DESC")
    List<Object[]> findAlgorithmDistribution();
}
