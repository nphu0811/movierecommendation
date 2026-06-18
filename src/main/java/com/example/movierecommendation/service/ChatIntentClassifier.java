package com.example.movierecommendation.service;

import com.example.movierecommendation.dto.ChatIntent;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Service
public class ChatIntentClassifier {

    public ChatIntent classify(String message) {
        if (message == null || message.trim().isEmpty()) {
            return ChatIntent.OUT_OF_SCOPE;
        }

        String normalized = removeAccent(message.toLowerCase().trim());

        // 1. GREETING
        if (isGreeting(normalized)) {
            return ChatIntent.GREETING;
        }

        // 2. ACCOUNT_HELP
        if (isAccountHelp(normalized)) {
            return ChatIntent.ACCOUNT_HELP;
        }

        // 3. WATCHLIST_HELP
        if (isWatchlistHelp(normalized)) {
            return ChatIntent.WATCHLIST_HELP;
        }

        // 4. RATING_HELP
        if (isRatingHelp(normalized)) {
            return ChatIntent.RATING_HELP;
        }

        // 5. HISTORY_HELP
        if (isHistoryHelp(normalized)) {
            return ChatIntent.HISTORY_HELP;
        }

        // 6. SITE_NAVIGATION
        if (isSiteNavigation(normalized)) {
            return ChatIntent.SITE_NAVIGATION;
        }

        // 7. MOVIE_INFO
        if (isMovieInfo(normalized)) {
            return ChatIntent.MOVIE_INFO;
        }

        // 8. MOVIE_SEARCH
        if (isMovieSearch(normalized)) {
            return ChatIntent.MOVIE_SEARCH;
        }

        // 9. MOVIE_RECOMMENDATION
        if (isMovieRecommendation(normalized)) {
            return ChatIntent.MOVIE_RECOMMENDATION;
        }

        // 10. Fallback: if it mentions general movie terms, assume recommendation
        if (containsMovieKeywords(normalized)) {
            return ChatIntent.MOVIE_RECOMMENDATION;
        }

        return ChatIntent.OUT_OF_SCOPE;
    }

    private String removeAccent(String s) {
        if (s == null) return null;
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String result = pattern.matcher(temp).replaceAll("");
        return result.replaceAll("[đĐ]", "d");
    }

    private boolean isGreeting(String s) {
        String[] greetings = {"hello", "hi", "xin chao", "chao ban", "chao", "alo", "helo", "hey", "chao bot", "chao ad", "chao admin"};
        for (String g : greetings) {
            if (s.equals(g) || s.startsWith(g + " ") || s.startsWith(g + "?") || s.startsWith(g + "!")) {
                return s.split("\\s+").length <= 4;
            }
        }
        return false;
    }

    private boolean isAccountHelp(String s) {
        return s.contains("tai khoan") || s.contains("profile") || s.contains("ho so") ||
               s.contains("mat khau") || s.contains("password") || s.contains("dang ky") ||
               s.contains("dang nhap") || s.contains("dang xuat") || s.contains("sua thong tin") ||
               s.contains("cap nhat thong tin") || s.contains("doi thong tin") ||
               s.contains("doi mat khau") || s.contains("register") || s.contains("login") || s.contains("logout");
    }

    private boolean isWatchlistHelp(String s) {
        return s.contains("watchlist") || s.contains("danh sach xem") || s.contains("danh sach yeu thich") ||
               s.contains("danh sach luu") || s.contains("luu phim") || s.contains("them phim vao") ||
               s.contains("them vao watchlist") || s.contains("xoa khoi watchlist") || s.contains("bo phim khoi") ||
               s.contains("bo khoi watchlist") || s.contains("add watchlist") || s.contains("remove watchlist");
    }

    private boolean isRatingHelp(String s) {
        return s.contains("danh gia") || s.contains("rate") || s.contains("rating") ||
               s.contains("review") || s.contains("binh luan") || s.contains("nhan xet") ||
               s.contains("cho diem") || s.contains("sao") || s.contains("viet binh luan");
    }

    private boolean isHistoryHelp(String s) {
        return s.contains("lich su") || s.contains("history") || s.contains("da xem") ||
               s.contains("xem gan day") || s.contains("vua xem") || s.contains("nhat ky xem") ||
               s.contains("nhat ki xem");
    }

    private boolean isSiteNavigation(String s) {
        return s.contains("dieu huong") || s.contains("navigation") || s.contains("nav") ||
               s.contains("thanh tim kiem") || s.contains("search bar") || s.contains("trang chu") ||
               s.contains("trang for you") || s.contains("for you") || s.contains("chuc nang") ||
               s.contains("tinh nang") || s.contains("menu") || s.contains("huong dan su dung") ||
               s.contains("huong dan dung") || s.contains("cach dung");
    }

    private boolean isMovieInfo(String s) {
        return s.contains("phim nay noi ve gi") || s.contains("phim nay co gi") ||
               s.contains("thong tin phim") || s.contains("thong tin cua phim") ||
               s.contains("tom tat phim") || s.contains("noi dung phim") ||
               s.contains("chi tiet phim") || s.contains("cot truyen") ||
               s.contains("phim do noi ve gi") || s.contains("phim do co gi");
    }

    private boolean isMovieSearch(String s) {
        return s.contains("tim phim") || s.contains("tim kiem phim") ||
               s.contains("search phim") || s.contains("tra cuu phim") ||
               s.contains("tim kiem") || s.contains("kiem phim");
    }

    private boolean isMovieRecommendation(String s) {
        return s.contains("goi y") || s.contains("de xuat") || s.contains("recommend") ||
               s.contains("tu van") || s.contains("hay") || s.contains("phim nao hot") ||
               s.contains("phim nao hay") || s.contains("thich xem") || s.contains("phim giong") ||
               s.contains("phim tuong tu") || s.contains("hanh dong") || s.contains("vien tuong") ||
               s.contains("kinh di") || s.contains("hai huoc") || s.contains("tinh cam") ||
               s.contains("hoat hinh") || s.contains("phieu luu") || s.contains("giat gan") ||
               s.contains("chinh kich") || s.contains("bi an") || s.contains("comedy") ||
               s.contains("romance") || s.contains("sci-fi") || s.contains("horror") ||
               s.contains("action") || s.contains("thriller") || s.contains("drama") ||
               s.contains("animation") || s.contains("phim hanh dong") || s.contains("phim hai") ||
               s.contains("phim ma") || s.contains("phim tinh cam") || s.contains("phim phieu luu");
    }

    private boolean containsMovieKeywords(String s) {
        return s.contains("phim") || s.contains("movie") || s.contains("cinema") ||
               s.contains("dien vien") || s.contains("dao dien") || s.contains("trailer") ||
               s.contains("dien anh");
    }
}
