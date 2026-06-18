package com.example.movierecommendation.algorithm;

import com.example.movierecommendation.entity.*;
import com.example.movierecommendation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);

    @Autowired private RatingRepository ratingRepository;
    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SimilarMovieRepository similarMovieRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;
    @Autowired private GenreRepository genreRepository;

    @org.springframework.beans.factory.annotation.Value("${recommendation.alpha:0.40}")
    private double alpha;

    @org.springframework.beans.factory.annotation.Value("${recommendation.beta:0.40}")
    private double beta;

    @org.springframework.beans.factory.annotation.Value("${recommendation.gamma:0.20}")
    private double gamma;

    @org.springframework.beans.factory.annotation.Value("${recommendation.max.recommendations:20}")
    private int maxRecommendations;

    @org.springframework.beans.factory.annotation.Value("${recommendation.min.common.ratings:2}")
    private int minCommonRatings;

    @org.springframework.beans.factory.annotation.Value("${recommendation.neighbor.count:10}")
    private int neighborCount;

    @org.springframework.beans.factory.annotation.Value("${recommendation.candidate.limit:200}")
    private int candidateLimit;

    @org.springframework.beans.factory.annotation.Value("${recommendation.popular.limit:50}")
    private int popularLimit;

    @org.springframework.beans.factory.annotation.Value("${recommendation.top.genres:5}")
    private int topGenres;

    @org.springframework.beans.factory.annotation.Value("${recommendation.similar.movies.limit:6}")
    private int similarMoviesLimit;

    /**
     * Phim không nên gợi ý lại: đã xem (watch_history) hoặc đã chấm điểm (rating).
     */
    public Set<Integer> getExcludedMovieIdsForRecommendations(Integer userId) {
        return Collections.unmodifiableSet(buildExcludedMovieIds(userId));
    }

    private Set<Integer> buildExcludedMovieIds(Integer userId) {
        Set<Integer> ids = new HashSet<>();
        List<Integer> watched = watchHistoryRepository.findWatchedMovieIdsByUserId(userId);
        if (watched != null) ids.addAll(watched);
        List<Integer> rated = ratingRepository.findRatedMovieIdsByUserId(userId);
        if (rated != null) ids.addAll(rated);
        return ids;
    }

    private List<Integer> excludeListForQuery(Set<Integer> excluded) {
        return excluded.isEmpty() ? Collections.singletonList(-1) : new ArrayList<>(excluded);
    }

    @Cacheable(value = "recommendations", key = "#userId")
    public List<Movie> getRecommendations(Integer userId) {
        Set<Integer> excluded = buildExcludedMovieIds(userId);
        List<Rating> userRatings = ratingRepository.findByUserUserId(userId);

        Map<Integer, Double> contentScores    = computeContentBasedScores(userId, userRatings);
        Map<Integer, Double> collabScores     = computeCollaborativeScores(userId, userRatings);
        Map<Integer, Double> popularityScores = computePopularityScores(userId);

        Set<Integer> allCandidates = new HashSet<>();
        allCandidates.addAll(contentScores.keySet());
        allCandidates.addAll(collabScores.keySet());
        allCandidates.addAll(popularityScores.keySet());

        Map<Integer, Double> scoreMap = new HashMap<>();
        for (Integer movieId : allCandidates) {
            if (excluded.contains(movieId)) continue;
            double score = alpha * getOrDefault(contentScores, movieId)
                         + beta  * getOrDefault(collabScores, movieId)
                         + gamma * getOrDefault(popularityScores, movieId);
            scoreMap.put(movieId, score);
        }

        List<Map.Entry<Integer, Double>> entries = new ArrayList<>(scoreMap.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Integer> topIds = new ArrayList<>();
        for (int i = 0; i < Math.min(maxRecommendations, entries.size()); i++) {
            topIds.add(entries.get(i).getKey());
        }

        if (topIds.isEmpty()) {
            return movieRepository.findMostWatchedMoviesExcludingUserInteractions(userId, PageRequest.of(0, maxRecommendations));
        }

        List<Movie> movies = new ArrayList<>(movieRepository.findAllByIdWithGenres(topIds));
        final Map<Integer, Double> finalScoreMap = scoreMap;
        movies.sort((a, b) -> Double.compare(
            getOrDefault(finalScoreMap, b.getMovieId()),
            getOrDefault(finalScoreMap, a.getMovieId())
        ));
        return movies;
    }

    public List<Movie> getSimilarMovies(Movie targetMovie, Integer currentUserId) {
        List<Movie> storedSimilar = similarMovieRepository
            .findByMovieMovieIdOrderBySimilarityScoreDesc(targetMovie.getMovieId(), PageRequest.of(0, similarMoviesLimit))
            .stream()
            .map(SimilarMovie::getSimilarMovie)
            .filter(movie -> movie.getDeletedAt() == null)
            .toList();
        if (!storedSimilar.isEmpty()) {
            return currentUserId == null ? storedSimilar : removeExcluded(storedSimilar, buildExcludedMovieIds(currentUserId));
        }

        List<Integer> targetGenreIds = new ArrayList<>();
        if (targetMovie.getGenres() != null) {
            for (Genre g : targetMovie.getGenres()) targetGenreIds.add(g.getGenreId());
        }

        if (currentUserId != null) {
            if (targetGenreIds.isEmpty()) {
                return movieRepository.findMostWatchedExcludingTargetAndUser(
                    targetMovie.getMovieId(), currentUserId, PageRequest.of(0, similarMoviesLimit));
            }
            List<Movie> similar = new ArrayList<>(movieRepository.findSimilarByGenresExcludingUser(
                targetGenreIds, targetMovie.getMovieId(), currentUserId, PageRequest.of(0, candidateLimit)));
            similar.sort((a, b) -> Double.compare(calculateSimilarityScore(b, targetMovie), calculateSimilarityScore(a, targetMovie)));
            List<Movie> result = new ArrayList<>();
            for (int i = 0; i < Math.min(similarMoviesLimit, similar.size()); i++) result.add(similar.get(i));
            return result;
        }

        List<Integer> excludeIds = excludeListForQuery(new HashSet<>(Collections.singletonList(targetMovie.getMovieId())));

        if (targetGenreIds.isEmpty()) {
            return movieRepository.findMostWatchedMoviesExcluding(excludeIds, PageRequest.of(0, similarMoviesLimit));
        }

        List<Movie> similar = new ArrayList<>(movieRepository.findByGenreIdsAndNotInIds(targetGenreIds, excludeIds, PageRequest.of(0, candidateLimit)));
        similar.sort((a, b) -> Double.compare(calculateSimilarityScore(b, targetMovie), calculateSimilarityScore(a, targetMovie)));

        List<Movie> result = new ArrayList<>();
        for (int i = 0; i < Math.min(similarMoviesLimit, similar.size()); i++) result.add(similar.get(i));
        return result;
    }

    private List<Movie> removeExcluded(List<Movie> movies, Set<Integer> excluded) {
        if (excluded.isEmpty()) return movies;
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies) {
            if (!excluded.contains(movie.getMovieId())) result.add(movie);
        }
        return result;
    }

    public List<Movie> getGenreBasedRecommendations(Integer userId) {
        List<Integer> topGenreIds = getTopGenreIdsForUser(userId);

        if (topGenreIds.isEmpty()) {
            return movieRepository.findTopRatedMoviesExcludingUserInteractions(userId, PageRequest.of(0, maxRecommendations));
        }
        return movieRepository.findByGenreIdsExcludingUserInteractions(topGenreIds, userId, PageRequest.of(0, maxRecommendations));
    }

    public List<Movie> getTrendingMovies(int limit) {
        return movieRepository.findMostWatchedMovies(PageRequest.of(0, limit));
    }

    // ── CONTENT-BASED ─────────────────────────────────────────────

    private Map<Integer, Double> computeContentBasedScores(Integer userId,
                                                            List<Rating> userRatings) {
        Map<Integer, Double> scores = new HashMap<>();
        Map<Integer, Double> genreProfile = buildGenreProfile(userId, userRatings);
        if (genreProfile.isEmpty()) return scores;

        List<Map.Entry<Integer, Double>> profileEntries = new ArrayList<>(genreProfile.entrySet());
        profileEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Integer> topGenreIds = new ArrayList<>();
        for (int i = 0; i < Math.min(topGenres, profileEntries.size()); i++) topGenreIds.add(profileEntries.get(i).getKey());

        List<Movie> candidates = movieRepository.findByGenreIdsExcludingUserInteractions(
            topGenreIds, userId, PageRequest.of(0, candidateLimit));

        double maxWeight = genreProfile.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        for (Movie movie : candidates) {
            double score = 0.0;
            if (movie.getGenres() != null) {
                for (Genre g : movie.getGenres()) score += getOrDefault(genreProfile, g.getGenreId());
            }
            int genreCount = (movie.getGenres() != null && !movie.getGenres().isEmpty()) ? movie.getGenres().size() : 1;
            scores.put(movie.getMovieId(), Math.min(score / (maxWeight * genreCount), 1.0));
        }
        return scores;
    }

    private Map<Integer, Double> buildGenreProfile(Integer userId, List<Rating> userRatings) {
        Map<Integer, Double> genreProfile = new HashMap<>();
        for (Rating r : userRatings) {
            Movie movie = r.getMovie();
            if (movie == null || movie.getGenres() == null) continue;
            for (Genre g : movie.getGenres()) {
                genreProfile.merge(g.getGenreId(), r.getRating(), Double::sum);
            }
        }
        if (genreProfile.isEmpty() && userId != null) {
            Optional<UserPreference> prefOpt = userPreferenceRepository.findByUserUserId(userId);
            if (prefOpt.isPresent()) {
                UserPreference pref = prefOpt.get();
                if (pref.getPreferredGenres() != null && !pref.getPreferredGenres().trim().isEmpty()) {
                    String[] genres = pref.getPreferredGenres().split(",");
                    for (String gStr : genres) {
                        gStr = gStr.trim();
                        if (!gStr.isEmpty()) {
                            try {
                                Integer gid = Integer.parseInt(gStr);
                                genreProfile.put(gid, 5.0);
                            } catch (NumberFormatException e) {
                                Optional<Genre> dbGenre = genreRepository.findByGenreName(gStr);
                                dbGenre.ifPresent(genre -> genreProfile.put(genre.getGenreId(), 5.0));
                            }
                        }
                    }
                }
            }
        }
        return genreProfile;
    }

    private Map<Integer, Double> computeCollaborativeScores(Integer userId, List<Rating> userRatings) {
        Map<Integer, Double> scores = new HashMap<>();
        if (userRatings.isEmpty()) return scores;

        Map<Integer, Double> targetVector = toRatingVector(userRatings);
        List<Integer> movieIds = new ArrayList<>(targetVector.keySet());
        List<Integer> candidateUserIds = ratingRepository.findUserIdsWithCommonMovies(movieIds, userId);

        if (candidateUserIds.isEmpty()) return scores;

        // Lấy ratings chỉ của candidate users (giới hạn theo candidateLimit)
        List<Integer> limitedCandidates = candidateUserIds.size() > candidateLimit
            ? candidateUserIds.subList(0, candidateLimit) : candidateUserIds;

        List<Rating> neighborRatings = ratingRepository.findByUserUserIdIn(limitedCandidates);

        // Group by userId
        Map<Integer, List<Rating>> byUser = new HashMap<>();
        for (Rating r : neighborRatings) {
            int uid = r.getUser().getUserId();
            byUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(r);
        }

        // Compute similarities
        List<double[]> simData = new ArrayList<>(); // [userId, similarity]
        for (Map.Entry<Integer, List<Rating>> entry : byUser.entrySet()) {
            Map<Integer, Double> otherVector = toRatingVector(entry.getValue());
            long commonCount = targetVector.keySet().stream().filter(otherVector::containsKey).count();
            if (commonCount < minCommonRatings) continue;
            double sim = cosineSimilarity(targetVector, otherVector);
            if (sim > 0) simData.add(new double[]{entry.getKey(), sim});
        }

        // Sort by sim desc, take top neighborCount
        simData.sort((a, b) -> Double.compare(b[1], a[1]));
        int topNeighborCount = Math.min(neighborCount, simData.size());
        if (topNeighborCount == 0) return scores;

        Set<Integer> excludeSet = buildExcludedMovieIds(userId);

        Map<Integer, Double> weightedSum = new HashMap<>();
        Map<Integer, Double> simSum = new HashMap<>();

        for (int i = 0; i < topNeighborCount; i++) {
            int neighborId = (int) simData.get(i)[0];
            double sim = simData.get(i)[1];
            List<Rating> nRatings = byUser.getOrDefault(neighborId, Collections.emptyList());
            for (Rating r : nRatings) {
                int movieId = r.getMovie().getMovieId();
                if (excludeSet.contains(movieId)) continue;
                weightedSum.merge(movieId, sim * r.getRating(), Double::sum);
                simSum.merge(movieId, sim, Double::sum);
            }
        }

        for (Map.Entry<Integer, Double> e : weightedSum.entrySet()) {
            double predicted = e.getValue() / simSum.get(e.getKey());
            scores.put(e.getKey(), (predicted - 1.0) / 4.0);
        }
        return scores;
    }

    private double cosineSimilarity(Map<Integer, Double> v1, Map<Integer, Double> v2) {
        Set<Integer> common = new HashSet<>(v1.keySet());
        common.retainAll(v2.keySet());
        if (common.isEmpty()) return 0.0;
        double dot = 0, norm1 = 0, norm2 = 0;
        for (int id : common) dot += v1.get(id) * v2.get(id);
        for (double val : v1.values()) norm1 += val * val;
        for (double val : v2.values()) norm2 += val * val;
        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private Map<Integer, Double> toRatingVector(List<Rating> ratings) {
        Map<Integer, Double> vector = new HashMap<>();
        for (Rating r : ratings) {
            vector.putIfAbsent(r.getMovie().getMovieId(), r.getRating());
        }
        return vector;
    }

    // ── POPULARITY ────────────────────────────────────────────────

    private Map<Integer, Double> computePopularityScores(Integer userId) {
        Map<Integer, Double> scores = new HashMap<>();
        List<Movie> popular = movieRepository.findMostWatchedMoviesExcludingUserInteractions(userId, PageRequest.of(0, popularLimit));
        if (popular.isEmpty()) return scores;
        int maxRank = popular.size();
        for (int i = 0; i < popular.size(); i++) {
            Movie m = popular.get(i);
            scores.put(m.getMovieId(), 1.0 - (double) i / maxRank);
        }
        return scores;
    }

    // ── HELPERS ───────────────────────────────────────────────────

    private List<Integer> getTopGenreIdsForUser(Integer userId) {
        List<Rating> ratings = ratingRepository.findByUserUserId(userId);
        Map<Integer, Double> genreProfile = buildGenreProfile(userId, ratings);
        List<Map.Entry<Integer, Double>> entries = new ArrayList<>(genreProfile.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topGenres, entries.size()); i++) result.add(entries.get(i).getKey());
        return result;
    }

    private double genreOverlap(Movie movie, List<Integer> targetGenreIds) {
        if (movie.getGenres() == null || movie.getGenres().isEmpty()) return 0.0;
        long overlap = movie.getGenres().stream()
            .filter(g -> targetGenreIds.contains(g.getGenreId())).count();
        return (double) overlap / Math.max(targetGenreIds.size(), movie.getGenres().size());
    }

    private double getOrDefault(Map<Integer, Double> map, Integer key) {
        return map.getOrDefault(key, 0.0);
    }

    public double calculateSimilarityScore(Movie a, Movie b) {
        // genre overlap
        double genreOverlapVal = 0.0;
        if (a.getGenres() != null && b.getGenres() != null && !a.getGenres().isEmpty() && !b.getGenres().isEmpty()) {
            Set<Integer> setA = new HashSet<>();
            for (Genre g : a.getGenres()) setA.add(g.getGenreId());
            Set<Integer> setB = new HashSet<>();
            for (Genre g : b.getGenres()) setB.add(g.getGenreId());
            long intersect = setA.stream().filter(setB::contains).count();
            Set<Integer> union = new HashSet<>(setA);
            union.addAll(setB);
            genreOverlapVal = (double) intersect / union.size();
        }

        // tag overlap
        double tagOverlapVal = 0.0;
        if (a.getTags() != null && b.getTags() != null && !a.getTags().isEmpty() && !b.getTags().isEmpty()) {
            Set<String> setA = new HashSet<>();
            for (Tag t : a.getTags()) if (t.getTag() != null) setA.add(t.getTag().toLowerCase().trim());
            Set<String> setB = new HashSet<>();
            for (Tag t : b.getTags()) if (t.getTag() != null) setB.add(t.getTag().toLowerCase().trim());
            long intersect = setA.stream().filter(setB::contains).count();
            Set<String> union = new HashSet<>(setA);
            union.addAll(setB);
            tagOverlapVal = union.isEmpty() ? 0.0 : (double) intersect / union.size();
        }

        // description keyword overlap
        double keywordOverlapVal = 0.0;
        if (a.getDescription() != null && b.getDescription() != null) {
            Set<String> setA = extractKeywords(a.getDescription());
            Set<String> setB = extractKeywords(b.getDescription());
            long intersect = setA.stream().filter(setB::contains).count();
            Set<String> union = new HashSet<>(setA);
            union.addAll(setB);
            keywordOverlapVal = union.isEmpty() ? 0.0 : (double) intersect / union.size();
        }

        // actor director overlap
        double actorDirectorOverlapVal = 0.0;
        Set<String> crewA = new HashSet<>();
        if (a.getActorsText() != null) {
            for (String act : a.getActorsText().split(",")) crewA.add(act.trim().toLowerCase());
        }
        if (a.getDirectorsText() != null) {
            for (String dir : a.getDirectorsText().split(",")) crewA.add(dir.trim().toLowerCase());
        }
        Set<String> crewB = new HashSet<>();
        if (b.getActorsText() != null) {
            for (String act : b.getActorsText().split(",")) crewB.add(act.trim().toLowerCase());
        }
        if (b.getDirectorsText() != null) {
            for (String dir : b.getDirectorsText().split(",")) crewB.add(dir.trim().toLowerCase());
        }
        if (!crewA.isEmpty() && !crewB.isEmpty()) {
            long intersect = crewA.stream().filter(crewB::contains).count();
            Set<String> union = new HashSet<>(crewA);
            union.addAll(crewB);
            actorDirectorOverlapVal = union.isEmpty() ? 0.0 : (double) intersect / union.size();
        }

        return genreOverlapVal * 0.5
             + tagOverlapVal * 0.2
             + keywordOverlapVal * 0.2
             + actorDirectorOverlapVal * 0.1;
    }

    private Set<String> extractKeywords(String text) {
        Set<String> words = new HashSet<>();
        if (text == null) return words;
        String[] split = text.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+");
        for (String w : split) {
            if (w.length() > 3) {
                words.add(w);
            }
        }
        return words;
    }
}
