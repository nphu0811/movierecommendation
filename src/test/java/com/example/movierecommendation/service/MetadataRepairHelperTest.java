package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Link;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.LinkRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataRepairHelperTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private LinkRepository linkRepository;

    @InjectMocks
    private MetadataRepairHelper repairHelper;

    @Test
    void whenTmdbIdIsAlreadyMappedToAnotherMovie_skipsUpdate() {
        Movie movie = new Movie();
        movie.setMovieId(10);
        movie.setTitle("Interstellar");

        when(movieRepository.findById(10)).thenReturn(Optional.of(movie));

        // Mock an existing link belonging to another movie (ID = 20)
        Link existingLink = new Link();
        existingLink.setMovieId(20);
        existingLink.setTmdbId(157336);

        when(linkRepository.findByTmdbId(157336)).thenReturn(Optional.of(existingLink));

        // Run
        repairHelper.updateMovieMetadata(10, 157336, "poster", "backdrop", "desc", "trailer");

        // Verify: link was NOT saved, and movie was NOT saved to avoid duplicate violation
        verify(linkRepository, never()).save(any(Link.class));
        verify(movieRepository, never()).save(any(Movie.class));
    }
}
