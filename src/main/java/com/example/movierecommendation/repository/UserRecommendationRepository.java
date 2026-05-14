package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.UserRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, Integer> {
    List<UserRecommendation> findByUserUserIdAndAlgorithmTypeOrderByScoreDescGeneratedAtDesc(
        Integer userId, String algorithmType, Pageable pageable);
    void deleteByUserUserIdAndAlgorithmType(Integer userId, String algorithmType);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserRecommendation ur WHERE ur.user.userId = :userId AND ur.algorithmType = :algorithmType")
    void deleteAndFlushByUserIdAndAlgorithmType(@Param("userId") Integer userId, @Param("algorithmType") String algorithmType);
}
