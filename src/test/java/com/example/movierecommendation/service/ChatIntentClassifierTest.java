package com.example.movierecommendation.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.movierecommendation.dto.ChatIntent;
import org.junit.jupiter.api.Test;

public class ChatIntentClassifierTest {

    private final ChatIntentClassifier classifier = new ChatIntentClassifier();

    @Test
    public void testAccountHelp() {
        assertEquals(ChatIntent.ACCOUNT_HELP, classifier.classify("sửa thông tin tài khoản như nào?"));
        assertEquals(ChatIntent.ACCOUNT_HELP, classifier.classify("đổi mật khẩu ở đâu?"));
        assertEquals(ChatIntent.ACCOUNT_HELP, classifier.classify("Cập nhật thông tin profile"));
    }

    @Test
    public void testWatchlistHelp() {
        assertEquals(ChatIntent.WATCHLIST_HELP, classifier.classify("thêm phim vào watchlist sao?"));
        assertEquals(ChatIntent.WATCHLIST_HELP, classifier.classify("thêm vào watchlist"));
        assertEquals(ChatIntent.WATCHLIST_HELP, classifier.classify("bỏ phim khỏi watchlist"));
    }

    @Test
    public void testRatingHelp() {
        assertEquals(ChatIntent.RATING_HELP, classifier.classify("đánh giá phim như nào?"));
        assertEquals(ChatIntent.RATING_HELP, classifier.classify("bình luận phim ở đâu"));
        assertEquals(ChatIntent.RATING_HELP, classifier.classify("cho điểm bộ phim này"));
    }

    @Test
    public void testHistoryHelp() {
        assertEquals(ChatIntent.HISTORY_HELP, classifier.classify("xem lịch sử xem ở đâu"));
        assertEquals(ChatIntent.HISTORY_HELP, classifier.classify("lich su xem phim"));
    }

    @Test
    public void testSiteNavigation() {
        assertEquals(ChatIntent.SITE_NAVIGATION, classifier.classify("trang for you ở đâu"));
        assertEquals(ChatIntent.SITE_NAVIGATION, classifier.classify("hướng dẫn sử dụng website"));
    }

    @Test
    public void testMovieSearchAndRecommendation() {
        ChatIntent intent1 = classifier.classify("tìm phim hành động vui");
        assertTrue(intent1 == ChatIntent.MOVIE_SEARCH || intent1 == ChatIntent.MOVIE_RECOMMENDATION);

        assertEquals(ChatIntent.MOVIE_RECOMMENDATION, classifier.classify("gợi ý phim giống Interstellar"));
        assertEquals(ChatIntent.MOVIE_RECOMMENDATION, classifier.classify("recommend phim hài"));
        assertEquals(ChatIntent.MOVIE_RECOMMENDATION, classifier.classify("Tôi thích phim khoa học viễn tưởng"));
    }

    @Test
    public void testGreeting() {
        assertEquals(ChatIntent.GREETING, classifier.classify("hello"));
        assertEquals(ChatIntent.GREETING, classifier.classify("xin chào"));
        assertEquals(ChatIntent.GREETING, classifier.classify("alo"));
    }

    @Test
    public void testOutOfScope() {
        assertEquals(ChatIntent.OUT_OF_SCOPE, classifier.classify("cách nấu phở"));
        assertEquals(ChatIntent.OUT_OF_SCOPE, classifier.classify("học java ở đâu"));
        assertEquals(ChatIntent.OUT_OF_SCOPE, classifier.classify("thời tiết hôm nay thế nào"));
    }
}
