package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Integer> {
    List<Watchlist> findByUserUserIdOrderByAddedAtDesc(Integer userId);
    Optional<Watchlist> findByUserUserIdAndMovieMovieId(Integer userId, Integer movieId);
    boolean existsByUserUserIdAndMovieMovieId(Integer userId, Integer movieId);
    void deleteByUserUserIdAndMovieMovieId(Integer userId, Integer movieId);
}
