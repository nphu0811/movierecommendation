package com.example.movierecommendation.service;

import com.example.movierecommendation.dto.MovieDetailDTO;
import com.example.movierecommendation.entity.Link;
import com.example.movierecommendation.entity.Movie;
import com.example.movierecommendation.entity.Rating;
import com.example.movierecommendation.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MovieFacade {

    @Autowired
    private MovieService movieService;
    
    @Autowired
    private InteractionService interactionService;
    
    @Autowired
    private RecommendationService recommendationService;
    
    @Autowired
    private UserService userService;

    public MovieDetailDTO getMovieDetail(Integer movieId, String username) {
        Optional<Movie> opt = movieService.findById(movieId);
        if (opt.isEmpty()) return null;

        Movie movie = opt.get();
        MovieDetailDTO dto = new MovieDetailDTO();
        dto.setMovie(movie);
        dto.setComments(interactionService.getCommentsByMovie(movieId));

        // Load IMDb/TMDB link
        interactionService.getLinkForMovie(movieId).ifPresent(dto::setMovieLink);

        // Load top tags for this movie
        dto.setTopTags(interactionService.getTopTagsForMovie(movieId));

        Integer currentUserId = null;
        if (username != null) {
            User currentUser = userService.getCurrentUser(username);
            currentUserId = currentUser.getUserId();
            dto.setCurrentUser(currentUser);

            Optional<Rating> ratingOpt = interactionService.getUserRating(currentUserId, movieId);
            dto.setUserRating(ratingOpt.isPresent() ? ratingOpt.get().getRating() : 0);
            dto.setInWatchlist(interactionService.isInWatchlist(currentUserId, movieId));
            dto.setHasWatched(interactionService.hasWatched(currentUserId, movieId));
        }

        dto.setSimilarMovies(recommendationService.getSimilarMovies(movie, currentUserId));
        return dto;
    }
}
