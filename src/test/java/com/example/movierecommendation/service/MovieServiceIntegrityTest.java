package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Genre;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.GenreRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieServiceIntegrityTest {

    @Mock MovieRepository movieRepository;
    @Mock GenreRepository genreRepository;

    private MovieService service;

    @BeforeEach
    void setUp() {
        service = new MovieService();
        ReflectionTestUtils.setField(service, "movieRepository", movieRepository);
        ReflectionTestUtils.setField(service, "genreRepository", genreRepository);
    }

    @Test
    void latestReleasesUseReleaseYearQuery() {
        Movie latest = new Movie();
        latest.setTitle("Latest catalog movie");
        when(movieRepository.findLatestReleases(any(Pageable.class))).thenReturn(List.of(latest));

        assertEquals(List.of(latest), service.getLatestReleases(8));
        verify(movieRepository).findLatestReleases(any(Pageable.class));
    }

    @Test
    void genreNameIsTrimmedBeforeSaving() {
        when(genreRepository.existsByGenreNameIgnoreCase("Science Fiction")).thenReturn(false);
        when(genreRepository.save(any(Genre.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Genre saved = service.createGenre("  Science   Fiction  ");

        assertEquals("Science Fiction", saved.getGenreName());
    }

    @Test
    void testGenreNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createGenre(" TestGenre "));
        assertThrows(IllegalArgumentException.class, () -> service.createGenre("New Genre"));
    }
}
