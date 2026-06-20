package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Movie;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TmdbMetadataValidatorTest {

    @Test
    void acceptsTrailingArticleAndMatchingYear() {
        Movie movie = movie("Big Green, The", 1995);
        assertTrue(TmdbMetadataValidator.matches(movie, Map.of(
            "title", "The Big Green",
            "original_title", "The Big Green",
            "release_date", "1995-09-29"
        )));
    }

    @Test
    void acceptsLocalAlternateTitleSuffix() {
        Movie movie = movie("King Kong vs. Godzilla (Kingukongu tai Gojira)", 1962);
        assertTrue(TmdbMetadataValidator.matches(movie, Map.of(
            "title", "King Kong vs. Godzilla",
            "release_date", "1962-08-11"
        )));
    }

    @Test
    void rejectsWrongTitleEvenWhenExternalIdExists() {
        Movie movie = movie("Big Green, The", 1995);
        assertFalse(TmdbMetadataValidator.matches(movie, Map.of(
            "title", "The Indian in the Cupboard",
            "release_date", "1995-07-14"
        )));
    }

    @Test
    void rejectsImplausibleReleaseYear() {
        Movie movie = movie("The Matrix", 1999);
        assertFalse(TmdbMetadataValidator.matches(movie, Map.of(
            "title", "The Matrix",
            "release_date", "2021-12-16"
        )));
    }

    @Test
    void acceptsAmpersandMapping() {
        Movie movie = movie("Smiling Fish and Goat on Fire", 1999);
        assertTrue(TmdbMetadataValidator.matches(movie, Map.of(
            "title", "Smiling Fish & Goat On Fire",
            "release_date", "1999-09-16"
        )));
    }

    @Test
    void acceptsSubstringTitleWithExactYearMatch() {
        Movie movie = movie("The Brothers Grimsby", 2016);
        assertTrue(TmdbMetadataValidator.matches(movie, Map.of(
            "title", "Grimsby",
            "release_date", "2016-02-24"
        )));
    }

    @Test
    void acceptsReleaseYearDifferenceUpToFiveYears() {
        Movie movie = movie("Fantasticks, The", 1995);
        assertTrue(TmdbMetadataValidator.matches(movie, Map.of(
            "title", "The Fantasticks",
            "release_date", "2000-09-22"
        )));
    }

    private Movie movie(String title, int year) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setReleaseYear(year);
        return movie;
    }
}
