package com.example.movierecommendation.service;

import com.example.movierecommendation.dto.ChatIntent;
import org.springframework.stereotype.Service;

@Service
public class ChatHelpService {

    public String getHelpResponse(ChatIntent intent) {
        switch (intent) {
            case ACCOUNT_HELP:
                return "Bạn có thể sửa thông tin tài khoản bằng cách đăng nhập, sau đó bấm vào mục Profile/Tài khoản ở thanh điều hướng. Tại trang hồ sơ, chọn chỉnh sửa thông tin nếu hệ thống có hỗ trợ. Nếu chưa thấy nút chỉnh sửa, chức năng này có thể chưa được triển khai trong phiên bản hiện tại.";
            case WATCHLIST_HELP:
                return "Để thêm phim vào Watchlist, bạn hãy truy cập trang chi tiết của bộ phim đó rồi bấm nút **+ Watchlist**. Để xóa phim khỏi danh sách, bạn chỉ cần bấm lại nút đó hoặc quản lý trực tiếp trong trang Watchlist cá nhân từ thanh điều hướng.";
            case RATING_HELP:
                return "Bạn có thể đánh giá phim bằng cách vào trang chi tiết của bộ phim đó, cuộn xuống phần Đánh giá & Bình luận (Ratings & Reviews), chọn số sao mong muốn từ 1 đến 5 và nhấn nút gửi đánh giá.";
            case HISTORY_HELP:
                return "Để xem lại lịch sử các phim đã xem (Watch History), bạn hãy bấm vào mục **Profile** hoặc trang cá nhân của mình từ thanh điều hướng, sau đó chọn mục **Lịch sử xem** để theo dõi chi tiết.";
            case SITE_NAVIGATION:
                return "Hệ thống MovieRec có các chức năng chính trên thanh điều hướng:\n" +
                       "- **Trang chủ**: Nơi khám phá các phim mới, phim hot và các bộ phim được đề xuất.\n" +
                       "- **Thanh tìm kiếm**: Nhanh chóng tra cứu bất kỳ bộ phim nào theo tên hoặc thể loại.\n" +
                       "- **Watchlist**: Xem lại các bộ phim bạn đã lưu để xem sau.\n" +
                       "- **Profile (Hồ sơ)**: Nơi xem lịch sử xem phim và chỉnh sửa thông tin tài khoản của bạn.";
            case GREETING:
                return "Xin chào! Mình là Trợ lý AI Movie Assistant. 🍿 Mình có thể giúp gì cho bạn hôm nay?\n" +
                       "Bạn có thể:\n" +
                       "- Yêu cầu gợi ý phim (ví dụ: 'tìm phim hành động vui', 'gợi ý phim giống Interstellar')\n" +
                       "- Hỏi cách sử dụng các tính năng của MovieRec như Watchlist, Đánh giá phim, hay Tài khoản cá nhân.";
            case MOVIE_INFO:
                return "Để xem thông tin chi tiết và tóm tắt của một bộ phim cụ thể, bạn vui lòng cho mình biết tên phim cụ thể (Ví dụ: 'phim Interstellar nói về gì') để mình hỗ trợ tìm kiếm, hoặc bạn có thể tìm kiếm phim đó trên thanh tìm kiếm và truy cập trang chi tiết nhé.";
            case OUT_OF_SCOPE:
                return "Xin lỗi bạn, mình là Trợ lý AI chuyên hỗ trợ các thông tin về phim ảnh và hướng dẫn sử dụng website MovieRec. Mình không thể giải đáp các câu hỏi ngoài phạm vi này. Bạn có muốn mình gợi ý bộ phim nào không?";
            default:
                return "Xin lỗi, mình chưa hiểu rõ yêu cầu. Bạn cần gợi ý phim hay hướng dẫn sử dụng chức năng nào của MovieRec?";
        }
    }
}
