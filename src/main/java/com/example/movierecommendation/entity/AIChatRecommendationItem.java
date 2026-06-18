package com.example.movierecommendation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_chat_recommendation_items")
public class AIChatRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private AIChatLog chatLog;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "rank_order")
    private Integer rankOrder;

    public AIChatRecommendationItem() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public AIChatLog getChatLog() { return chatLog; }
    public void setChatLog(AIChatLog chatLog) { this.chatLog = chatLog; }

    public Movie getMovie() { return movie; }
    public void setMovie(Movie movie) { this.movie = movie; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getRankOrder() { return rankOrder; }
    public void setRankOrder(Integer rankOrder) { this.rankOrder = rankOrder; }
}
