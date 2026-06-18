package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.VideoTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoTimelineRepository extends JpaRepository<VideoTimeline, Integer> {
    List<VideoTimeline> findByMovieMovieIdOrderByTimestampSecondsAsc(Integer movieId);
}
