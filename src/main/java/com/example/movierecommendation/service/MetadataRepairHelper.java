package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Link;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.repository.LinkRepository;
import com.example.movierecommendation.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
class MetadataRepairHelper {

    @Autowired private MovieRepository movieRepository;
    @Autowired private LinkRepository linkRepository;

    @Transactional
    public void deleteLink(Integer movieId) {
        Movie managedMovie = movieRepository.findById(movieId).orElse(null);
        if (managedMovie != null) {
            Link link = managedMovie.getLink();
            if (link != null) {
                linkRepository.delete(link);
                managedMovie.setLink(null);
            }
            managedMovie.setPosterUrl(null);
            managedMovie.setBackdropUrl(null);
            managedMovie.setDescription(null);
            managedMovie.setTrailerKey(null);
            managedMovie.setMetadataSource(null);
            managedMovie.setMetadataVerifiedAt(null);
            movieRepository.save(managedMovie);
        }
    }

    @Transactional
    public void updateMovieMetadata(Integer movieId, Integer tmdbId, String posterUrl, String backdropUrl, String description, String trailerKey) {
        Movie managedMovie = movieRepository.findById(movieId).orElse(null);
        if (managedMovie != null) {
            Link link = managedMovie.getLink();
            if (link == null) {
                link = new Link();
                link.setMovie(managedMovie);
            }
            link.setTmdbId(tmdbId);
            linkRepository.save(link);
            managedMovie.setLink(link);

            managedMovie.setPosterUrl(posterUrl);
            managedMovie.setBackdropUrl(backdropUrl);
            managedMovie.setDescription(description);
            managedMovie.setTrailerKey(trailerKey);
            managedMovie.setMetadataSource("TMDB");
            managedMovie.setMetadataVerifiedAt(LocalDateTime.now());
            movieRepository.save(managedMovie);
        }
    }
}
