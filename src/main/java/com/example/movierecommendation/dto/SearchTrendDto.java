package com.example.movierecommendation.dto;

public class SearchTrendDto {
    private String query;
    private Long count;

    public SearchTrendDto() {}

    public SearchTrendDto(String query, Long count) {
        this.query = query;
        this.count = count;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }
}
