package com.example.movierecommendation.repository;

import com.example.movierecommendation.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Integer> {
    Optional<Genre> findByGenreName(String genreName);
    Optional<Genre> findByGenreNameIgnoreCase(String genreName);
    boolean existsByGenreName(String genreName);
    boolean existsByGenreNameIgnoreCase(String genreName);

    @Query("SELECT g FROM Genre g WHERE LOWER(TRIM(g.genreName)) NOT IN ('testgenre', 'new genre') ORDER BY g.genreName")
    List<Genre> findAllPublicGenres();
}
