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
        boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());

        switch (intent) {
            case ACCOUNT_HELP:
                if (normalized.contains("dang ky") || normalized.contains("tao tai khoan") || 
                    normalized.contains("sign up") || normalized.contains("register")) {
                    return isVi 
                        ? "Để đăng ký tài khoản mới, bạn hãy nhấn vào nút **Đăng ký (Register)** ở thanh điều hướng hoặc nút **Create Free Account** trên trang chủ. Điền các thông tin như Tên đăng nhập, Email, Mật khẩu, sau đó nhập mã xác thực OTP gửi về email của bạn để hoàn tất kích hoạt."
                        : "To register a new account, please click the **Register** button in the navigation bar or the **Create Free Account** button on the home page. Fill in details like Username, Email, Password, and then enter the OTP verification code sent to your email to complete activation.";
                }
                if (normalized.contains("dang nhap") || normalized.contains("login") || normalized.contains("signin")) {
                    return isVi
                        ? "Để đăng nhập, bạn hãy nhấn nút **Đăng nhập (Login)** ở góc trên bên phải màn hình, nhập Email/Username và Mật khẩu của bạn, sau đó nhấn **Đăng nhập**."
                        : "To log in, please click the **Login** button at the top right of the screen, enter your Email/Username and Password, and click **Login**.";
                }
                if (normalized.contains("quen mat khau") || normalized.contains("lay lai mat khau") || 
                    normalized.contains("lost password") || normalized.contains("reset password") || 
                    normalized.contains("khoi phuc mat khau") || normalized.contains("lay mat khau")) {
                    return isVi
                        ? "Nếu bạn quên mật khẩu, hãy vào trang **Đăng nhập**, nhấp vào liên kết **Quên mật khẩu?**. Nhập email đăng ký của bạn để hệ thống gửi mã OTP khôi phục mật khẩu. Nhập mã OTP đó để tiến hành đặt mật khẩu mới."
                        : "If you forgot your password, go to the **Login** page and click the **Forgot password?** link. Enter your registered email to receive an OTP verification code. Enter that OTP to set a new password.";
                }
                if (normalized.contains("doi mat khau") || normalized.contains("change password")) {
                    return isVi
                        ? "Để đổi mật khẩu, bạn cần đăng nhập, sau đó truy cập trang **Profile/Tài khoản** ở thanh điều hướng. Tại đây, chọn mục **Đổi mật khẩu**, nhập mật khẩu cũ, mật khẩu mới và xác nhận."
                        : "To change your password, you need to log in, then go to the **Profile** page in the navigation bar. Choose **Change Password**, enter your old password, new password, and confirm.";
                }
                // Default Profile edit response
                return isVi
                    ? "Bạn có thể sửa thông tin tài khoản bằng cách đăng nhập, sau đó bấm vào mục Profile/Tài khoản ở thanh điều hướng. Tại trang hồ sơ, chọn chỉnh sửa thông tin nếu hệ thống có hỗ trợ. Nếu chưa thấy nút chỉnh sửa, chức năng này có thể chưa được triển khai trong phiên bản hiện tại."
                    : "You can edit your account information by logging in, then clicking the Profile section in the navigation bar. On the profile page, choose to edit details if supported. If you don't see an edit button, this feature might not be implemented in the current version.";

            case WATCHLIST_HELP:
                if (normalized.contains("xoa") || normalized.contains("bo") || 
                    normalized.contains("remove") || normalized.contains("delete")) {
                    return isVi
                        ? "Để xóa phim khỏi danh sách Watchlist, bạn có thể bấm lại nút **Đã yêu thích / Watchlist** ở trang chi tiết phim để tắt kích hoạt, hoặc vào trực tiếp trang **Watchlist** cá nhân của mình từ thanh điều hướng rồi bấm nút loại bỏ phim."
                        : "To remove a movie from your Watchlist, click the **Favorite / Watchlist** button on the movie detail page to deactivate it, or go directly to your personal **Watchlist** page from the navigation bar and click the remove button.";
                }
                return isVi
                    ? "Để thêm phim vào Watchlist, bạn hãy truy cập trang chi tiết của bộ phim đó rồi bấm nút **+ Watchlist** (hoặc nút **Yêu thích**). Danh sách các phim đã lưu có thể được quản lý tại trang **Watchlist** cá nhân của bạn từ thanh điều hướng."
                    : "To add a movie to your Watchlist, go to the detail page of that movie and click the **+ Watchlist** button. You can manage your saved movies on your personal **Watchlist** page in the navigation bar.";

            case RATING_HELP:
                return isVi
                    ? "Bạn có thể đánh giá phim bằng cách vào trang chi tiết của bộ phim đó, cuộn xuống phần Đánh giá & Bình luận (Ratings & Reviews), chọn số sao mong muốn từ 1 đến 5 và nhấn nút gửi đánh giá."
                    : "You can rate a movie by visiting its detail page, scrolling down to the Ratings & Reviews section, choosing the desired stars from 1 to 5, and clicking the submit rating button.";

            case HISTORY_HELP:
                return isVi
                    ? "Để xem lại lịch sử các phim đã xem (Watch History), bạn hãy bấm vào mục **Profile** hoặc trang cá nhân của mình từ thanh điều hướng, sau đó chọn mục **Lịch sử xem** để theo dõi chi tiết."
                    : "To view your Watch History, click on **Profile** in the navigation bar and select the **Watch History** section to view details.";

            case SITE_NAVIGATION:
                return isVi
                    ? "Hệ thống MovieRec có các chức năng chính trên thanh điều hướng:\n" +
                       "- **Trang chủ**: Nơi khám phá các phim mới, phim hot và các bộ phim được đề xuất.\n" +
                       "- **Thanh tìm kiếm**: Nhanh chóng tra cứu bất kỳ bộ phim nào theo tên hoặc thể loại.\n" +
                       "- **Watchlist**: Xem lại các bộ phim bạn đã lưu để xem sau.\n" +
                       "- **Profile (Hồ sơ)**: Nơi xem lịch sử xem phim và chỉnh sửa thông tin tài khoản của bạn."
                    : "The MovieRec system has the following main features in the navigation bar:\n" +
                       "- **Home**: Discover new, hot, and recommended movies.\n" +
                       "- **Search Bar**: Quickly search for any movie by title or genre.\n" +
                       "- **Watchlist**: Review the movies you saved to watch later.\n" +
                       "- **Profile**: View your watch history and edit your account information.";

            case GREETING:
                return isVi
                    ? "Xin chào! Mình là Trợ lý AI Movie Assistant. 🍿 Mình có thể giúp gì cho bạn hôm nay?\n" +
                       "Bạn có thể:\n" +
                       "- Yêu cầu gợi ý phim (ví dụ: 'tìm phim hành động vui', 'gợi ý phim giống Interstellar')\n" +
                       "- Hỏi cách sử dụng các tính năng của MovieRec như Watchlist, Đánh giá phim, hay Tài khoản cá nhân."
                    : "Hello! I am your AI Movie Assistant. 🍿 How can I help you today?\n" +
                       "- Request movie recommendations (e.g., 'find a fun action movie', 'recommend movies like Interstellar')\n" +
                       "- Ask how to use MovieRec features like the Watchlist, movie ratings, or your personal account.";

            case MOVIE_INFO:
                return isVi
                    ? "Để xem thông tin chi tiết và tóm tắt của một bộ phim cụ thể, bạn vui lòng cho mình biết tên phim cụ thể (Ví dụ: 'phim Interstellar nói về gì') để mình hỗ trợ tìm kiếm, hoặc bạn có thể tìm kiếm phim đó trên thanh tìm kiếm và truy cập trang chi tiết nhé."
                    : "To view detailed information and a summary of a specific movie, please let me know the movie's title (e.g., 'what is Interstellar about') so I can search for it, or you can find it via the search bar and visit its detail page.";

            case OUT_OF_SCOPE:
                return isVi
                    ? "Xin lỗi bạn, mình là Trợ lý AI chuyên hỗ trợ các thông tin về phim ảnh và hướng dẫn sử dụng website MovieRec. Mình không thể giải đáp các câu hỏi ngoài phạm vi này. Bạn có muốn mình gợi ý bộ phim nào không?"
                    : "Sorry, I am an AI Assistant specialized in movie info and guide for MovieRec. I cannot answer out-of-scope questions. Would you like me to recommend a movie instead?";

            default:
                return isVi
                    ? "Xin lỗi, mình chưa hiểu rõ yêu cầu. Bạn cần gợi ý phim hay hướng dẫn sử dụng chức năng nào của MovieRec?"
                    : "Sorry, I didn't quite understand your request. Do you need movie recommendations or help with a feature of MovieRec?";
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
