package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.AIChatRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AIChatRecommendationItemRepository extends JpaRepository<AIChatRecommendationItem, Integer> {
}
