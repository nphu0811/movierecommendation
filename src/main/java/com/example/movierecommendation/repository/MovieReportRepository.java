package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.MovieReport;
import com.example.movierecommendation.entity.MovieReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieReportRepository extends JpaRepository<MovieReport, Integer> {
    Page<MovieReport> findByStatus(MovieReportStatus status, Pageable pageable);
}
