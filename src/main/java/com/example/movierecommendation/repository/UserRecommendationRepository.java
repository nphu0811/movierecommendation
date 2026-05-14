package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.UserRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, Integer> {
    List<UserRecommendation> findByUserUserIdAndAlgorithmTypeOrderByScoreDescGeneratedAtDesc(
        Integer userId, String algorithmType, Pageable pageable);
    void deleteByUserUserIdAndAlgorithmType(Integer userId, String algorithmType);
}
