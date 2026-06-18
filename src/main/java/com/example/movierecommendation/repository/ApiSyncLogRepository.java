package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.ApiSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiSyncLogRepository extends JpaRepository<ApiSyncLog, Integer> {
    Page<ApiSyncLog> findAll(Pageable pageable);
}
