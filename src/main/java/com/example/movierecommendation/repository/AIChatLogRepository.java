package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.AIChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIChatLogRepository extends JpaRepository<AIChatLog, Integer> {
    List<AIChatLog> findByUserUserIdOrderByCreatedAtDesc(Integer userId);

    @Query(value = "SELECT c FROM AIChatLog c LEFT JOIN FETCH c.user ORDER BY c.createdAt DESC",
           countQuery = "SELECT COUNT(c) FROM AIChatLog c")
    Page<AIChatLog> findAllWithUser(Pageable pageable);

    @Query("SELECT c FROM AIChatLog c LEFT JOIN FETCH c.recommendationItems WHERE c.chatId = :id")
    java.util.Optional<AIChatLog> findByIdWithRecommendations(@org.springframework.data.repository.query.Param("id") Integer id);
}

