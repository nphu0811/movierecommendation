package com.example.movierecommendation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "video_timelines", indexes = {
    @Index(name = "idx_video_timeline_movie", columnList = "movie_id")
})
public class VideoTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "timeline_id")
    private Integer timelineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "timestamp_seconds", nullable = false)
    private Integer timestampSeconds;

    @Column(name = "event_description", columnDefinition = "TEXT")
    private String eventDescription;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    public VideoTimeline() {}

    public VideoTimeline(Movie movie, Integer timestampSeconds, String eventDescription, String transcriptText) {
        this.movie = movie;
        this.timestampSeconds = timestampSeconds;
        this.eventDescription = eventDescription;
        this.transcriptText = transcriptText;
    }

    public Integer getTimelineId() { return timelineId; }
    public void setTimelineId(Integer timelineId) { this.timelineId = timelineId; }

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }

    public Integer getTimestampSeconds() { return timestampSeconds; }
    public void setTimestampSeconds(Integer timestampSeconds) { this.timestampSeconds = timestampSeconds; }

    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }

    public String getTranscriptText() { return transcriptText; }
    public void setTranscriptText(String transcriptText) { this.transcriptText = transcriptText; }
}
