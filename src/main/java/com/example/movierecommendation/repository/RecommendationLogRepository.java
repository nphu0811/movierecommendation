package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.RecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Integer> {
}
