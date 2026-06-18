package com.example.movierecommendation.service;

import com.example.movierecommendation.dto.ChatIntent;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Service
public class ChatHelpService {

    public String getHelpResponse(ChatIntent intent) {
        return getHelpResponse(intent, "");
    }

    public String getHelpResponse(ChatIntent intent, String userMessage) {
        if (userMessage == null) userMessage = "";
        String normalized = removeAccent(userMessage.toLowerCase().trim());

        switch (intent) {
            case ACCOUNT_HELP:
                if (normalized.contains("dang ky") || normalized.contains("tao tai khoan") || 
                    normalized.contains("sign up") || normalized.contains("register")) {
                    return "Để đăng ký tài khoản mới, bạn hãy nhấn vào nút **Đăng ký (Register)** ở thanh điều hướng hoặc nút **Create Free Account** trên trang chủ. Điền các thông tin như Tên đăng nhập, Email, Mật khẩu, sau đó nhập mã xác thực OTP gửi về email của bạn để hoàn tất kích hoạt.";
                }
                if (normalized.contains("dang nhap") || normalized.contains("login") || normalized.contains("signin")) {
                    return "Để đăng nhập, bạn hãy nhấn nút **Đăng nhập (Login)** ở góc trên bên phải màn hình, nhập Email/Username và Mật khẩu của bạn, sau đó nhấn **Đăng nhập**.";
                }
                if (normalized.contains("quen mat khau") || normalized.contains("lay lai mat khau") || 
                    normalized.contains("lost password") || normalized.contains("reset password") || 
                    normalized.contains("khoi phuc mat khau") || normalized.contains("lay mat khau")) {
                    return "Nếu bạn quên mật khẩu, hãy vào trang **Đăng nhập**, nhấp vào liên kết **Quên mật khẩu?**. Nhập email đăng ký của bạn để hệ thống gửi mã OTP khôi phục mật khẩu. Nhập mã OTP đó để tiến hành đặt mật khẩu mới.";
                }
                if (normalized.contains("doi mat khau") || normalized.contains("change password")) {
                    return "Để đổi mật khẩu, bạn cần đăng nhập, sau đó truy cập trang **Profile/Tài khoản** ở thanh điều hướng. Tại đây, chọn mục **Đổi mật khẩu**, nhập mật khẩu cũ, mật khẩu mới và xác nhận.";
                }
                // Default Profile edit response
                return "Bạn có thể sửa thông tin tài khoản bằng cách đăng nhập, sau đó bấm vào mục Profile/Tài khoản ở thanh điều hướng. Tại trang hồ sơ, chọn chỉnh sửa thông tin nếu hệ thống có hỗ trợ. Nếu chưa thấy nút chỉnh sửa, chức năng này có thể chưa được triển khai trong phiên bản hiện tại.";

            case WATCHLIST_HELP:
                if (normalized.contains("xoa") || normalized.contains("bo") || 
                    normalized.contains("remove") || normalized.contains("delete")) {
                    return "Để xóa phim khỏi danh sách Watchlist, bạn có thể bấm lại nút **Đã yêu thích / Watchlist** ở trang chi tiết phim để tắt kích hoạt, hoặc vào trực tiếp trang **Watchlist** cá nhân của mình từ thanh điều hướng rồi bấm nút loại bỏ phim.";
                }
                return "Để thêm phim vào Watchlist, bạn hãy truy cập trang chi tiết của bộ phim đó rồi bấm nút **+ Watchlist** (hoặc nút **Yêu thích**). Danh sách các phim đã lưu có thể được quản lý tại trang **Watchlist** cá nhân của bạn từ thanh điều hướng.";

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

    private String removeAccent(String s) {
        if (s == null) return null;
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String result = pattern.matcher(temp).replaceAll("");
        return result.replaceAll("[đĐ]", "d");
    }
}
