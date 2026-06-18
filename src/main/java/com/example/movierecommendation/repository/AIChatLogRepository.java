package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.AIChatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIChatLogRepository extends JpaRepository<AIChatLog, Integer> {
    List<AIChatLog> findByUserUserIdOrderByCreatedAtDesc(Integer userId);
}
