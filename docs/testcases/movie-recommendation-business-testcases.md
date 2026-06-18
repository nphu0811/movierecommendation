# MovieRecommendation Business Test Cases

Bảng dưới đây thống kê đầy đủ các kịch bản kiểm thử nghiệp vụ cho hệ thống gợi ý phim.

---

## 1. Recommendation (Gợi ý phim)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-REC-001** | User mới nhận gợi ý phổ biến | Recommendation | User | User mới chưa rating, chưa xem phim | 1. Đăng ký hoặc dùng tài khoản user mới.<br>2. Đăng nhập.<br>3. Vào trang chủ. | Tài khoản mới: `new_user` | - Hệ thống không lỗi.<br>- Hiển thị trending/top-rated/new releases.<br>- Có reason fallback: “Vì phim này đang phổ biến và được đánh giá cao trên hệ thống.” | P0 | Passed |
| **TC-REC-002** | Rating ảnh hưởng đến gợi ý | Recommendation | User | DB có nhiều phim thuộc thể loại Action | 1. Đăng nhập.<br>2. Rate 5 sao cho ít nhất 3 phim Action.<br>3. Refresh trang chủ. | 3 phim thể loại Action | - Gợi ý ưu tiên các phim cùng hoặc gần thể loại Action.<br>- Lý do gợi ý có nhắc đến thể loại Action: "Vì bạn đã xem/đánh giá cao nhiều phim thuộc thể loại Action." | P0 | Passed |
| **TC-REC-003** | Rating thấp ảnh hưởng đến gợi ý | Recommendation | User | DB có phim Horror | 1. Đăng nhập.<br>2. Rate 1 hoặc 2 sao cho vài phim Horror.<br>3. Refresh recommendation. | Phim Horror | - Hệ thống hạn chế/không gợi ý phim Horror trong danh sách Personalized Recommendations. | P0 | Passed |
| **TC-REC-004** | Watch history ảnh hưởng đến gợi ý | Recommendation | User | Có phim Comedy | 1. Đăng nhập.<br>2. Xem phim Comedy và kéo progress > 80%.<br>3. Vào trang chủ xem phần gợi ý. | Phim Comedy | - Gợi ý tăng các phim cùng thể loại Comedy.<br>- Lý do gợi ý nhắc đến lịch sử xem thể loại Comedy. | P0 | Passed |
| **TC-REC-005** | Không gợi ý lại phim đã xem/rated | Recommendation | User | User đã xem phim A | 1. Đăng nhập.<br>2. Rate hoặc xem phim A.<br>3. Vào danh sách gợi ý cá nhân hóa (Personalized Recommendations). | Phim A | - Phim A không còn xuất hiện trong danh sách Personalized Recommendations. | P0 | Passed |
| **TC-REC-006** | Similar movies ở trang chi tiết | Recommendation | Guest/User | Phim chi tiết có thể loại | 1. Truy cập chi tiết phim.<br>2. Xem danh sách "Similar Movies". | Phim có thể loại Action | - Hiển thị danh sách phim tương tự có cùng thể loại.<br>- Nếu không có phim cùng thể loại thì hiển thị phim xem nhiều nhất. | P1 | Passed |
| **TC-REC-007** | AI không bịa phim ngoài database | Recommendation | User | OpenAI enabled | 1. Gọi gợi ý cá nhân hóa.<br>2. Kiểm tra danh sách phim trả về có khớp DB không. | OpenAI API Key | - Chỉ hiển thị các phim tồn tại trong database.<br>- Các tựa phim do AI đề xuất nếu không khớp cơ sở dữ liệu sẽ bị bỏ qua. | P0 | Passed |
| **TC-REC-008** | Recommendation log được tạo | Recommendation | User/Admin | User kích hoạt gợi ý | 1. Đăng nhập và vào trang chủ.<br>2. Đăng nhập Admin và vào trang nhật ký thuật toán gợi ý. | Tài khoản user và admin | - Có record ghi nhật ký gợi ý được tạo.<br>- Chứa algorithm type, execution time, total movies, notes. | P1 | Passed |
| **TC-REC-009** | Hiển thị “Vì sao gợi ý phim này?” | Recommendation | User | User đăng nhập | 1. Vào trang chủ.<br>2. Di chuột (hover) vào nút "Vì sao gợi ý?". | Phim gợi ý | - Hiển thị lý do rõ ràng, chi tiết, không hiển thị null/undefined. | P0 | Passed |

---

## 2. Search / Search History (Tìm kiếm)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-SEARCH-001** | Search history được lưu | Search | Guest/User | Không | 1. Nhập từ khóa "batman".<br>2. Nhấn nút Tìm kiếm. | Keyword: `batman` | - Có record lưu trữ trong bảng `search_history`.<br>- Lưu query, normalized query, result count, source, latency. | P1 | Passed |
| **TC-SEARCH-002** | Click search result được ghi nhận | Search | Guest/User | Có lịch sử tìm kiếm vừa thực hiện | 1. Tìm kiếm từ khóa.<br>2. Click vào phim trong kết quả tìm kiếm. | Phim trong kết quả | - search_history được cập nhật `clicked_movie_id` và `clicked_at`. | P1 | Passed |
| **TC-SEARCH-003** | Admin xem top search keywords | Search | Admin | Hệ thống đã có lịch sử tìm kiếm | 1. Đăng nhập tài khoản admin.<br>2. Vào trang Thống kê & Analytics. | Tài khoản admin | - Hiển thị danh sách top từ khóa được tìm kiếm nhiều nhất và số lượt tìm kiếm. | P1 | Passed |
| **TC-SEARCH-004** | Search không có kết quả | Search | Guest/User | Từ khóa không khớp phim nào | 1. Search từ khóa không tồn tại. | Keyword: `xyz123abc` | - UI hiển thị thông báo không có kết quả.<br>- Hệ thống vẫn lưu record search_history với `result_count = 0`. | P1 | Passed |

---

## 3. Watchlist / Favorite

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-WL-001** | Toggle watchlist | Watchlist | User | Đã đăng nhập | 1. Vào trang chi tiết phim.<br>2. Click "Add to Watchlist".<br>3. Click lần nữa để xóa khỏi Watchlist. | Phim A | - Lần 1: Thêm thành công (nút đổi thành "In Watchlist").<br>- Lần 2: Xóa thành công (nút đổi thành "Add to Watchlist"). | P1 | Passed |
| **TC-WL-002** | Guest không được thêm watchlist | Watchlist | Guest | Chưa đăng nhập | 1. Vào chi tiết phim.<br>2. Click nút "Login to interact" để vào watchlist. | Phim A | - Yêu cầu đăng nhập, trả về trang đăng nhập hoặc không hiển thị nút thêm Watchlist trực tiếp. | P1 | Passed |
| **TC-FAV-001** | Favorite thống nhất với UI/README | Watchlist | Guest/User | Không | 1. Kiểm tra UI, README và mã nguồn. | Không | - Thống nhất sử dụng thuật ngữ "Watchlist / Danh sách xem sau", không gọi nhầm lẫn là Favorite để tránh mâu thuẫn. | P1 | Passed |

---

## 4. Comment / Review (Bình luận)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-REVIEW-001** | User viết comment hợp lệ | Comment | User | Đã đăng nhập | 1. Vào chi tiết phim.<br>2. Nhập comment hợp lệ.<br>3. Submit. | Comment: `Phim hay quá!` | - Bình luận xuất hiện ngay bên dưới danh sách bình luận của phim. | P1 | Passed |
| **TC-REVIEW-002** | Comment rỗng bị chặn | Comment | User | Đã đăng nhập | 1. Để trống khung comment hoặc nhập toàn khoảng trắng.<br>2. Submit. | Comment: `   ` | - Báo lỗi bình luận không được để trống.<br>- Không tạo comment trong DB. | P1 | Passed |
| **TC-REVIEW-003** | Comment chống XSS | Comment | User | Đã đăng nhập | 1. Nhập comment chứa mã Script.<br>2. Submit. | Comment: `<script>alert(1)</script>` | - Thêm thành công bình luận nhưng nội dung được escape hiển thị text thay vì thực thi Javascript. | P1 | Passed |
| **TC-ADMIN-REVIEW-001** | Admin xem danh sách comment | Comment | Admin | Đã đăng nhập Admin | 1. Đăng nhập Admin.<br>2. Vào mục "Kiểm duyệt comment" trên Sidebar. | Tài khoản admin | - Hiển thị danh sách đầy đủ các bình luận gồm: người dùng, phim, nội dung, thời gian, trạng thái. | P1 | Passed |
| **TC-ADMIN-REVIEW-002** | Admin ẩn/xóa mềm comment | Comment | Admin | Có bình luận hiện hữu | 1. Admin bấm "Ẩn đi" một bình luận.<br>2. Vào lại chi tiết phim xem bình luận đó. | Bình luận cần ẩn | - Bình luận đổi trạng thái thành "Đã ẩn" trên trang Admin.<br>- Bình luận không còn hiển thị ở chi tiết phim.<br>- Dữ liệu trong DB vẫn được giữ nguyên (deleted_at có giá trị). | P1 | Passed |

---

## 5. Report lỗi phim (Báo lỗi)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-REPORT-001** | User report lỗi phim | Report | User | Đã đăng nhập | 1. Vào chi tiết phim.<br>2. Bấm "Báo lỗi phim".<br>3. Chọn loại lỗi, nhập mô tả và Submit. | Loại lỗi: `BROKEN_TRAILER`, mô tả | - Tạo báo cáo lỗi thành công trong DB với trạng thái ban đầu là `NEW`. | P1 | Passed |
| **TC-REPORT-002** | Guest không được report | Report | Guest | Chưa đăng nhập | 1. Vào chi tiết phim.<br>2. Kiểm tra sự hiện diện của nút báo lỗi. | Phim A | - Nút báo lỗi ẩn hoặc không hiển thị, thay thế bằng nút yêu cầu đăng nhập. | P1 | Passed |
| **TC-REPORT-003** | Report OTHER bắt buộc có mô tả | Report | User | Đã đăng nhập | 1. Bấm báo lỗi phim.<br>2. Chọn loại lỗi `OTHER`.<br>3. Để trống mô tả và Submit. | Loại lỗi: `OTHER`, mô tả: rỗng | - Hệ thống báo lỗi yêu cầu bắt buộc nhập mô tả khi chọn lỗi khác. | P1 | Passed |
| **TC-ADMIN-REPORT-001** | Admin xem danh sách report | Report | Admin | Đã đăng nhập Admin | 1. Đăng nhập Admin.<br>2. Vào mục "Báo cáo lỗi" trên Sidebar. | Tài khoản admin | - Hiển thị danh sách báo cáo lỗi gồm: phim, người báo, loại lỗi, chi tiết, trạng thái, ngày báo cáo. | P1 | Passed |
| **TC-ADMIN-REPORT-002** | Admin xử lý report | Report | Admin | Có báo cáo lỗi NEW | 1. Bấm "Xử lý" tại báo cáo lỗi.<br>2. Chọn trạng thái `RESOLVED`.<br>3. Nhập ghi chú Admin và Submit. | Trạng thái: `RESOLVED`, ghi chú | - Trạng thái đổi thành `RESOLVED`.<br>- Cập nhật thời gian giải quyết `resolved_at` và ghi chú của Admin. | P1 | Passed |

---

## 6. Admin Dashboard & Analytics

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-ADMIN-001** | Admin xem dashboard cơ bản | Dashboard | Admin | Đã đăng nhập Admin | 1. Truy cập `/admin`. | Tài khoản admin | - Hiển thị chính xác tổng số user, phim, rating, comment, active users. | P1 | Passed |
| **TC-ADMIN-002** | Admin xem search analytics | Analytics | Admin | Đã có dữ liệu search_history | 1. Truy cập Sidebar -> "Thống kê & Analytics". | Tài khoản admin | - Xem được top từ khóa được tìm nhiều, top phim được click, số lượt search và các truy vấn không có kết quả. | P1 | Passed |
| **TC-ADMIN-003** | Admin xem recommendation analytics | Analytics | Admin | Đã có logs recommendation | 1. Truy cập Sidebar -> "Thống kê & Analytics". | Tài khoản admin | - Xem được phân bố thuật toán, top phim được gợi ý và nhật ký chạy thuật toán gợi ý gần đây. | P1 | Passed |
| **TC-ADMIN-004** | User thường không vào được trang admin | Security | User | Tài khoản ROLE_USER | 1. Đăng nhập user thường.<br>2. Truy cập trực tiếp địa chỉ `/admin`. | Tài khoản user | - Bị chặn truy cập, trả về mã lỗi 403 hoặc trang Access Denied. | P0 | Passed |

---

## 7. AI Chatbot gợi ý phim

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-AICHAT-001** | User nhập yêu cầu theo thể loại | AI Chatbot | User | Đăng nhập | 1. Vào AI Chat.<br>2. Nhập: “Gợi ý phim hành động vui, dễ xem”. | Tin nhắn: "Gợi ý phim hành động vui, dễ xem" | - Chatbot trả lời bằng tiếng Việt.<br>- Danh sách phim thuộc hoặc gần thể loại Action/Comedy/Adventure.<br>- Phim đều tồn tại trong DB. | P1 | Passed |
| **TC-AICHAT-002** | AI không bịa phim ngoài database | AI Chatbot | User | Không | 1. Nhập yêu cầu cụ thể.<br>2. Kiểm tra movieId của phim trả về. | Tin nhắn yêu cầu | - Mọi phim trả về đều có movieId hợp lệ trong database.<br>- Các phim không khớp candidates bị loại bỏ hoàn toàn. | P0 | Passed |
| **TC-AICHAT-003** | Guest dùng AI Chat | AI Chatbot | Guest | Chưa đăng nhập | 1. Vào AI Chat.<br>2. Nhập: “Tôi muốn phim kinh dị nhẹ”. | Tin nhắn: "Tôi muốn phim kinh dị nhẹ" | - Gợi ý dựa trên nội dung câu hỏi.<br>- Không sử dụng dữ liệu cá nhân hóa. | P2 | Passed |
| **TC-AICHAT-004** | User đã đăng nhập nhận gợi ý cá nhân hóa | AI Chatbot | User | Đã đăng nhập, rate cao Action | 1. Nhập: “Gợi ý phim giống gu của tôi”. | Tin nhắn: "Gợi ý phim giống gu của tôi" | - Dùng rating/watch history của user để cá nhân hóa.<br>- Phản hồi nhắc đến sở thích hoặc phim đã rate. | P1 | Passed |
| **TC-AICHAT-005** | Không tìm thấy phim phù hợp | AI Chatbot | User | Không | 1. Nhập yêu cầu quá hẹp (ví dụ: phim hoạt hình kinh dị viễn tưởng Việt Nam năm 1950). | Tin nhắn yêu cầu | - Không bịa phim ngoài cơ sở dữ liệu.<br>- Trả lời rõ: "Hiện hệ thống chưa có phim phù hợp với yêu cầu này." | P1 | Passed |
| **TC-AICHAT-006** | OpenAI API lỗi hoặc timeout | AI Chatbot | User | OpenAI bị lỗi (hoặc khóa key) | 1. Nhập yêu cầu bất kỳ khi OpenAI bị lỗi. | Tin nhắn yêu cầu | - Hệ thống không crash.<br>- Trả về gợi ý fallback từ danh sách phim phổ biến trong DB. | P1 | Passed |

---

## 8. Advanced Filter/Sort (Bộ lọc và sắp xếp nâng cao)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-FILTER-001** | Lọc phim theo thể loại | Filter/Sort | Guest/User | Không | 1. Vào danh sách phim.<br>2. Chọn thể loại Action. | Thể loại: Action | - Chỉ hiển thị các phim thuộc thể loại Action. | P1 | Passed |
| **TC-FILTER-002** | Lọc phim theo năm phát hành | Filter/Sort | Guest/User | Không | 1. Chọn năm phát hành (ví dụ: 2014). | Năm: 2014 | - Chỉ hiển thị các phim phát hành đúng năm 2014. | P2 | Passed |
| **TC-FILTER-003** | Sắp xếp theo top-rated | Filter/Sort | Guest/User | Không | 1. Chọn sắp xếp theo "Đánh giá cao nhất". | Sắp xếp: top_rated | - Danh sách phim được sắp xếp theo rating trung bình giảm dần. | P1 | Passed |
| **TC-FILTER-004** | Kết hợp từ khóa tìm kiếm và bộ lọc | Filter/Sort | Guest/User | Không | 1. Nhập từ khóa.<br>2. Chọn thể loại.<br>3. Chọn sắp xếp. | Keyword: `galaxy`, Genre: Action, Sort: top_rated | - Danh sách trả về thỏa mãn tất cả các điều kiện lọc đồng thời. | P1 | Passed |

---

## 9. User Preferences (Sở thích cá nhân)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-PREF-001** | User cập nhật sở thích | Preferences | User | Đăng nhập | 1. Vào trang cá nhân -> Preferences.<br>2. Chọn các thể loại yêu thích (Action, Adventure).<br>3. Bấm Lưu. | Thể loại: Action, Adventure | - Lưu thành công tùy chọn sở thích vào bảng `user_preferences`. | P1 | Passed |
| **TC-PREF-002** | User mới nhận gợi ý theo sở thích | Preferences | User | User mới chưa xem/rate, đã thiết lập sở thích | 1. Đăng nhập.<br>2. Thiết lập sở thích.<br>3. Vào trang chủ kiểm tra danh sách gợi ý. | Sở thích đã lưu | - Gợi ý ưu tiên các phim thuộc thể loại đã chọn trong preferences thay vì chỉ hiển thị phim hot chung chung. | P1 | Passed |

---

## 10. History Privacy (Quyền riêng tư lịch sử)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-HISTORY-001** | User xóa một mục lịch sử xem | Privacy | User | Có lịch sử xem phim | 1. Vào lịch sử xem.<br>2. Bấm nút xóa (thùng rác) ở phim A. | Phim A | - Phim A biến mất khỏi lịch sử xem của user đó.<br>- Lịch sử của các user khác không bị ảnh hưởng.<br>- Xóa mềm trong DB. | P2 | Passed |
| **TC-HISTORY-002** | User xóa toàn bộ lịch sử xem | Privacy | User | Có lịch sử xem phim | 1. Click "Xóa tất cả" tại lịch sử xem.<br>2. Xác nhận xóa. | Không | - Toàn bộ lịch sử xem của user bị ẩn đi.<br>- Personalized Recommendation cache được clear/cập nhật lại. | P2 | Passed |
| **TC-HISTORY-003** | User xóa lịch sử tìm kiếm | Privacy | User | Có lịch sử tìm kiếm | 1. Vào trang cá nhân -> Search History.<br>2. Xóa từng từ khóa hoặc Xóa tất cả. | Từ khóa tìm kiếm | - Lịch sử tìm kiếm của user biến mất trên giao diện.<br>- Record bị xóa cứng khỏi cơ sở dữ liệu. | P2 | Passed |

---

## 11. Actor/Director/Content-based (Siêu dữ liệu nâng cao)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-META-001** | Trang chi tiết hiển thị đạo diễn/diễn viên | Metadata | Guest/User | Phim có metadata | 1. Truy cập chi tiết phim A. | Phim A | - Hiển thị đúng thông tin Đạo diễn và Diễn viên chính của phim. | P2 | Passed |
| **TC-CONTENT-001** | Similar movies tính toán theo metadata nâng cao | Content-based | Guest/User | Có phim tương tự | 1. Truy cập chi tiết phim.<br>2. Xem danh sách Similar Movies. | Phim chi tiết | - Tính toán độ tương tự kết hợp 4 trọng số: thể loại (50%), tag (20%), từ khóa mô tả (20%), đạo diễn/diễn viên (10%).<br>- Các phim gợi ý có độ tương đồng cao hiển thị chính xác. | P2 | Passed |

---

## 12. API Sync Logs (Lịch sử đồng bộ)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-SYNC-001** | Admin xem sync logs | Sync Logs | Admin | Đăng nhập Admin | 1. Vào `/admin/sync-logs` trên Sidebar. | Tài khoản admin | - Hiển thị lịch sử các lần chạy seed dữ liệu hoặc TMDB import thành công gồm provider, action, status, successCount, finishedAt. | P2 | Passed |
| **TC-SYNC-002** | Sync lỗi được ghi nhận trong logs | Sync Logs | Admin | Xảy ra lỗi đồng bộ | 1. Giả lập lỗi TMDB (nhập sai API Key).<br>2. Vào xem nhật ký sync logs. | Sai API Key | - Ghi nhận log với status `FAILED` hoặc `PARTIAL_SUCCESS`.<br>- Cột errorMessage chứa thông tin lỗi chi tiết để debug. | P2 | Passed |

---

## 13. Demo Data (Dữ liệu demo bảo vệ đồ án)

| Test Case ID | Tên test | Module | Role | Tiền điều kiện | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Mức độ ưu tiên | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **TC-DEMO-001** | Khác biệt gợi ý giữa 2 demo users có gu khác nhau | Demo Data | Tester | DB đã seed 3 tài khoản demo | 1. Đăng nhập `action.demo@example.com` -> Ghi nhận gợi ý.<br>2. Đăng nhập `comedy.demo@example.com` -> Ghi nhận gợi ý. | Mật khẩu: `123456` | - User Action nhận gợi ý thiên về hành động/phiêu lưu.<br>- User Comedy nhận gợi ý thiên về hài kịch/lãng mạn.<br>- Lý do gợi ý giải thích rõ theo gu của từng người. | P0 | Passed |
| **TC-DEMO-002** | User mới nhận gợi ý fallback không lỗi | Demo Data | Tester | Không | 1. Đăng nhập `new.demo@example.com`.<br>2. Vào trang chủ xem phần gợi ý. | Mật khẩu: `123456` | - Gợi ý phổ biến (trending/top-rated) được hiển thị bình thường.<br>- Không lỗi do thiếu lịch sử xem/rating. | P0 | Passed |

