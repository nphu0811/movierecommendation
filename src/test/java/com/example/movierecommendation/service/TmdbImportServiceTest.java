package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Genre;
import com.example.movierecommendation.entity.Link;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.GenreRepository;
import com.example.movierecommendation.repository.LinkRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TmdbImportServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock MovieRepository movieRepository;
    @Mock LinkRepository linkRepository;
    @Mock GenreRepository genreRepository;

    @Test
    void retryUpsertsByTmdbIdInsteadOfCreatingDuplicateMovie() {
        Movie existing = new Movie();
        existing.setMovieId(42);
        existing.setTitle("Old title");
        Link link = new Link();
        link.setMovie(existing);
        link.setTmdbId(603);

        Genre action = new Genre();
        action.setGenreName("Action");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("results", List.of(Map.of(
            "id", 603,
            "title", "The Matrix",
            "release_date", "1999-03-30",
            "overview", "Correct overview",
            "poster_path", "/matrix.jpg",
            "genre_ids", List.of(28)
        ))));
        when(linkRepository.findByTmdbId(603)).thenReturn(Optional.of(link));
        when(genreRepository.findByGenreNameIgnoreCase("Action")).thenReturn(Optional.of(action));
        when(movieRepository.save(existing)).thenReturn(existing);

        TmdbImportService service = new TmdbImportService(restTemplate, movieRepository, linkRepository, genreRepository);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        service.importPopularMovies();

        assertEquals("The Matrix", existing.getTitle());
        assertEquals(1999, existing.getReleaseYear());
        assertEquals("TMDB", existing.getMetadataSource());
        verify(movieRepository).save(existing);
        verify(linkRepository, never()).save(any(Link.class));
    }

    @Test
    void newMoviePersistsStableExternalId() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("results", List.of(Map.of(
            "id", 278,
            "title", "The Shawshank Redemption",
            "release_date", "1994-09-23",
            "overview", "Correct overview",
            "genre_ids", List.of(18)
        ))));
        when(linkRepository.findByTmdbId(278)).thenReturn(Optional.empty());
        when(movieRepository.findFirstByTitleIgnoreCaseAndReleaseYearAndDeletedAtIsNull(anyString(), eq(1994)))
            .thenReturn(Optional.empty());
        Genre drama = new Genre();
        drama.setGenreName("Drama");
        when(genreRepository.findByGenreNameIgnoreCase("Drama")).thenReturn(Optional.of(drama));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> {
            Movie movie = invocation.getArgument(0);
            movie.setMovieId(99);
            return movie;
        });

        TmdbImportService service = new TmdbImportService(restTemplate, movieRepository, linkRepository, genreRepository);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        service.importPopularMovies();

        ArgumentCaptor<Link> captor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(captor.capture());
        assertEquals(278, captor.getValue().getTmdbId());
        assertEquals(99, captor.getValue().getMovie().getMovieId());
    }
}
