package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, Integer> {
    Optional<Link> findByMovieMovieId(Integer movieId);
}
