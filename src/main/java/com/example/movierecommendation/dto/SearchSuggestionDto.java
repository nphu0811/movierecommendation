package com.example.movierecommendation.dto;

public class SearchSuggestionDto {
    private String query;
    private Long count;

    public SearchSuggestionDto() {}

    public SearchSuggestionDto(String query, Long count) {
        this.query = query;
        this.count = count;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }
}
