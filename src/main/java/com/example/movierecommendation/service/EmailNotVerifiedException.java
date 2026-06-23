package com.example.movierecommendation.service;

public class EmailNotVerifiedException extends RuntimeException {
    private final String email;

    public EmailNotVerifiedException(String message, String email) {
        super(message);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
