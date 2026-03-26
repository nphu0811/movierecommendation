package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.Optional;

@Service
public class InteractionService {

    @Autowired
    private RatingRepository ratingRepository;
    @Autowired
    private WatchHistoryRepository watchHistoryRepository;
    @Autowired
    private WatchlistRepository watchlistRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private LinkRepository linkRepository;

    private final Map<String, Long> rateLimits = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000;

    private void checkRateLimit(Integer userId, String action) {
        String key = userId + ":" + action;
        long now = System.currentTimeMillis();
        long last = rateLimits.getOrDefault(key, 0L);
        if (now - last < COOLDOWN_MS) {
            throw new RuntimeException("Rate limit exceeded for " + action + ". Please wait.");
        }
        rateLimits.put(key, now);
    }

    // ──────────────────── RATING ────────────────────

    @Transactional
    public Rating rateMovie(Integer userId, Integer movieId, Integer score) {
        checkRateLimit(userId, "rateMovie");
        Optional<Rating> existing = ratingRepository.findByUserUserIdAndMovieMovieId(userId, movieId);
        Rating rating;
        if (existing.isPresent()) {
            rating = existing.get();
            rating.setRating(score);
        } else {
            User user = userRepository.findById(userId).orElseThrow();
            Movie movie = movieRepository.findById(movieId).orElseThrow();
            rating = new Rating();
            rating.setUser(user);
            rating.setMovie(movie);
            rating.setRating(score);
        }
        return ratingRepository.save(rating);
    }

    public Optional<Rating> getUserRating(Integer userId, Integer movieId) {
        return ratingRepository.findByUserUserIdAndMovieMovieId(userId, movieId);
    }

    public Double getAverageRating(Integer movieId) {
        return ratingRepository.findAverageRatingByMovieId(movieId);
    }

    public Long getRatingCount(Integer movieId) {
        return ratingRepository.countByMovieId(movieId);
    }

    public long countAllRatings() {
        return ratingRepository.countAllRatings();
    }

    // ──────────────────── WATCH HISTORY ────────────────────

    @Transactional
    public void markAsWatched(Integer userId, Integer movieId) {
        if (!watchHistoryRepository.existsByUserUserIdAndMovieMovieId(userId, movieId)) {
            User user = userRepository.findById(userId).orElseThrow();
            Movie movie = movieRepository.findById(movieId).orElseThrow();
            WatchHistory wh = new WatchHistory();
            wh.setUser(user);
            wh.setMovie(movie);
            watchHistoryRepository.save(wh);
        }
    }

    public List<WatchHistory> getWatchHistory(Integer userId) {
        return watchHistoryRepository.findByUserUserIdOrderByWatchedAtAsc(userId);
    }

    public boolean hasWatched(Integer userId, Integer movieId) {
        return watchHistoryRepository.existsByUserUserIdAndMovieMovieId(userId, movieId);
    }

    // ──────────────────── WATCHLIST ────────────────────

    @Transactional
    public boolean toggleWatchlist(Integer userId, Integer movieId) {
        if (watchlistRepository.existsByUserUserIdAndMovieMovieId(userId, movieId)) {
            watchlistRepository.deleteByUserUserIdAndMovieMovieId(userId, movieId);
            return false;
        } else {
            User user = userRepository.findById(userId).orElseThrow();
            Movie movie = movieRepository.findById(movieId).orElseThrow();
            Watchlist wl = new Watchlist();
            wl.setUser(user);
            wl.setMovie(movie);
            watchlistRepository.save(wl);
            return true;
        }
    }

    public List<Watchlist> getWatchlist(Integer userId) {
        return watchlistRepository.findByUserUserIdOrderByAddedAtAsc(userId);
    }

    public boolean isInWatchlist(Integer userId, Integer movieId) {
        return watchlistRepository.existsByUserUserIdAndMovieMovieId(userId, movieId);
    }

    // ──────────────────── COMMENTS ────────────────────

    @Transactional
    public Comment addComment(Integer userId, Integer movieId, String text) {
        checkRateLimit(userId, "addComment");
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }
        
        // Escape HTML to prevent XSS
        String sanitizedText = org.springframework.web.util.HtmlUtils.htmlEscape(text.trim());
        if (sanitizedText.length() > 500) {
            throw new IllegalArgumentException("Comment is too long");
        }

        User user = userRepository.findById(userId).orElseThrow();
        Movie movie = movieRepository.findById(movieId).orElseThrow();
        Comment comment = new Comment();
        comment.setUser(user);
        comment.setMovie(movie);
        comment.setCommentText(sanitizedText);
        return commentRepository.save(comment);
    }

    public List<Comment> getCommentsByMovie(Integer movieId) {
        return commentRepository.findByMovieMovieIdOrderByCreatedAtDesc(movieId);
    }

    @Transactional
    public void deleteComment(Integer commentId) {
        commentRepository.deleteById(commentId);
    }

    public long countAllComments() {
        return commentRepository.countAllComments();
    }

    public long countActiveUsers() {
        return watchHistoryRepository.countActiveUsers();
    }

    // ──────────────────── TAGS ────────────────────

    @Transactional
    public Tag addTag(Integer userId, Integer movieId, String tagText) {
        checkRateLimit(userId, "addTag");
        if (tagText == null || tagText.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag cannot be empty");
        }
        String sanitized = org.springframework.web.util.HtmlUtils.htmlEscape(tagText.trim().toLowerCase());
        if (sanitized.length() > 50) {
            throw new IllegalArgumentException("Tag too long (max 50 characters)");
        }
        if (tagRepository.existsByUserUserIdAndMovieMovieIdAndTag(userId, movieId, sanitized)) {
            throw new IllegalArgumentException("You already added this tag");
        }
        User user = userRepository.findById(userId).orElseThrow();
        Movie movie = movieRepository.findById(movieId).orElseThrow();
        Tag tag = new Tag();
        tag.setUser(user);
        tag.setMovie(movie);
        tag.setTag(sanitized);
        return tagRepository.save(tag);
    }

    public List<Tag> getTagsByMovie(Integer movieId) {
        return tagRepository.findByMovieMovieIdOrderByCreatedAtDesc(movieId);
    }

    @Transactional
    public void deleteTag(Integer tagId, Integer userId) {
        tagRepository.deleteByTagIdAndUserUserId(tagId, userId);
    }

    public List<Object[]> getTopTagsForMovie(Integer movieId) {
        return tagRepository.findTopTagsByMovieId(movieId);
    }

    // ──────────────────── LINKS ────────────────────

    public Optional<Link> getLinkForMovie(Integer movieId) {
        return linkRepository.findByMovieMovieId(movieId);
    }
}
