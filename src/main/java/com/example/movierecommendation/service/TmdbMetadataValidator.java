package com.example.movierecommendation.service;

import com.example.movierecommendation.entity.Movie;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Prevents a wrong external ID from overwriting a different movie's metadata. */
final class TmdbMetadataValidator {

    private static final Set<String> TRAILING_ARTICLES = Set.of("the", "a", "an");

    private TmdbMetadataValidator() {}

    static boolean matches(Movie movie, Map<?, ?> detail) {
        if (movie == null || detail == null) return false;

        String localTitle = canonicalTitle(movie.getTitle());
        String tmdbTitle = canonicalTitle(stringValue(detail.get("title")));
        String originalTitle = canonicalTitle(stringValue(detail.get("original_title")));
        
        boolean titleMatches = !localTitle.isEmpty() && (
            localTitle.equals(tmdbTitle) || 
            localTitle.equals(originalTitle) ||
            matchesSubTitle(localTitle, stringValue(detail.get("title"))) ||
            matchesSubTitle(localTitle, stringValue(detail.get("original_title")))
        );
        if (!titleMatches) return false;

        Integer localYear = movie.getReleaseYear();
        Integer externalYear = releaseYear(stringValue(detail.get("release_date")));
        // Allow up to 4 years difference for regional delayed releases
        return localYear == null || externalYear == null || Math.abs(localYear - externalYear) <= 4;
    }

    private static boolean matchesSubTitle(String localCanonical, String fullTitle) {
        if (fullTitle == null || fullTitle.isBlank()) return false;
        for (String part : fullTitle.split("[:\\-]")) {
            if (canonicalTitle(part).equals(localCanonical)) {
                return true;
            }
        }
        return false;
    }

    static String canonicalTitle(String title) {
        if (title == null) return "";
        String withoutAlternateTitle = title.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
        int comma = withoutAlternateTitle.lastIndexOf(',');
        if (comma > 0) {
            String suffix = withoutAlternateTitle.substring(comma + 1).trim().toLowerCase(Locale.ROOT);
            if (TRAILING_ARTICLES.contains(suffix)) {
                withoutAlternateTitle = suffix + " " + withoutAlternateTitle.substring(0, comma).trim();
            }
        }
        
        // Normalize and convert to lowercase
        String norm = Normalizer.normalize(withoutAlternateTitle, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);

        // Map common Roman numerals to Arabic numbers to align editions
        norm = norm.replaceAll("\\bviii\\b", "8")
                   .replaceAll("\\bvii\\b", "7")
                   .replaceAll("\\biii\\b", "3")
                   .replaceAll("\\bii\\b", "2")
                   .replaceAll("\\biv\\b", "4")
                   .replaceAll("\\bvi\\b", "6")
                   .replaceAll("\\bix\\b", "9")
                   .replaceAll("\\bv\\b", "5")
                   .replaceAll("\\bx\\b", "10");

        // Keep all alphanumeric Unicode characters (letters and digits across any language)
        return norm.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    static Integer releaseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) return null;
        try {
            return LocalDate.parse(releaseDate).getYear();
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
