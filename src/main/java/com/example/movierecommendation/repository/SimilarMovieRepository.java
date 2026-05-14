package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.SimilarMovie;
import com.example.movierecommendation.entity.SimilarMovieId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimilarMovieRepository extends JpaRepository<SimilarMovie, SimilarMovieId> {
    List<SimilarMovie> findByMovieMovieIdOrderBySimilarityScoreDesc(Integer movieId, Pageable pageable);
}
