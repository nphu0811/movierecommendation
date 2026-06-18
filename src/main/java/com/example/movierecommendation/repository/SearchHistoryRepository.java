package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /**
     * Trending searches: top normalized queries by count since a given time.
     * Returns Object[] where [0] = normalized_query (String), [1] = count (Long).
     */
    @Query(value = "SELECT sh.normalized_query, COUNT(*) AS total " +
                   "FROM search_history sh " +
                   "WHERE sh.created_at >= :since " +
                   "AND sh.normalized_query <> '' " +
                   "GROUP BY sh.normalized_query " +
                   "ORDER BY total DESC",
           nativeQuery = true)
    List<Object[]> findTrendingSearches(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Keyword suggestions by prefix.
     * normalized_query is already lowercase so no LOWER() needed.
     * Returns Object[] where [0] = normalized_query (String), [1] = count (Long).
     */
    @Query(value = "SELECT sh.normalized_query, COUNT(*) AS total " +
                   "FROM search_history sh " +
                   "WHERE sh.normalized_query LIKE :prefix || '%' " +
                   "AND sh.normalized_query <> '' " +
                   "GROUP BY sh.normalized_query " +
                   "ORDER BY total DESC",
           nativeQuery = true)
    List<Object[]> findSuggestionsByPrefix(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT sh.clickedMovie.title, COUNT(sh) as c FROM SearchHistory sh WHERE sh.clickedMovie IS NOT NULL GROUP BY sh.clickedMovie.title ORDER BY c DESC")
    List<Object[]> findTopClickedMovies(Pageable pageable);

    @Query("SELECT sh.searchQuery, COUNT(sh) as c FROM SearchHistory sh WHERE sh.resultCount = 0 GROUP BY sh.searchQuery ORDER BY c DESC")
    List<Object[]> findZeroResultQueries(Pageable pageable);

    @Query("SELECT AVG(sh.latencyMs) FROM SearchHistory sh WHERE sh.latencyMs IS NOT NULL")
    Double findAverageLatency();

    List<SearchHistory> findByUserUserIdOrderByCreatedAtDesc(Integer userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM SearchHistory sh WHERE sh.searchId = :searchId AND sh.user.userId = :userId")
    void deleteBySearchIdAndUserUserId(@Param("searchId") Long searchId, @Param("userId") Integer userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM SearchHistory sh WHERE sh.user.userId = :userId")
    void deleteByUserUserId(@Param("userId") Integer userId);
}
