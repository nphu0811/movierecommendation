# MovieRec

Hệ thống duyệt và gợi ý phim cá nhân hóa, xây dựng bằng Spring Boot, Thymeleaf và PostgreSQL. Hệ thống kết hợp content-based, collaborative filtering và popularity; mô hình AI tương thích OpenAI là lớp rerank tùy chọn, không phải nguồn gợi ý duy nhất.

[Live demo](https://movierecommendation-production-6e68.up.railway.app/)

## Chức năng đã triển khai

- Guest: xem danh sách/chi tiết phim, tìm kiếm, lọc genre và xem phim tương tự.
- User: đăng ký, đăng nhập, rating, comment, tag, watchlist, lịch sử xem và trang gợi ý cá nhân.
- Admin: quản lý phim, genre, user, comment, report, analytics và đồng bộ metadata.
- Recommendation: hybrid score, cold-start fallback, loại phim đã xem/đã rating, lưu kết quả và thời gian chạy.
- AI: chat và rerank tùy chọn; khi API lỗi hoặc chưa cấu hình, hệ thống dùng kết quả hybrid.
- Data enrichment: TMDB metadata được upsert theo `tmdb_id` và chỉ ghi khi title/year khớp.

## Công nghệ thực tế

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.4.5 |
| Web | Spring MVC, Thymeleaf, HTML/CSS/JavaScript |
| Security | Spring Security session, BCrypt, CSRF, role `USER`/`ADMIN` |
| Database | PostgreSQL 14+, Spring Data JPA, Flyway |
| Cache | Caffeine |
| External API | TMDB, OpenAI-compatible API |
| Build/deploy | Gradle, Docker, Railway |

Dự án hiện dùng session authentication, không dùng JWT.

## Cấu trúc source

```text
src/
├── main/
│   ├── java/com/example/movierecommendation/
│   │   ├── algorithm/RecommendationEngine.java
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   └── resources/
│       ├── db/migration/
│       ├── static/
│       ├── templates/
│       ├── application.properties.example
│       └── application-prod.properties
└── test/java/com/example/movierecommendation/
```

Source backend, template, migration và test đều nằm trong repo; package gốc là `com.example.movierecommendation`.

## Chạy local

Yêu cầu:

- Java 21
- PostgreSQL 14+

Tạo database trống, sau đó copy file cấu hình mẫu:

```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```

Cấu hình tối thiểu:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/movierecommendation
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

tmdb.api.key=
ai.api-key=
app.remember-me-key=a-random-secret-at-least-32-characters
app.demo.seed-enabled=false
```

Chạy ứng dụng:

```bash
./gradlew bootRun
```

Mặc định local dùng port cấu hình trong `application.properties`. Flyway tự tạo core schema trên database trống và tự chạy migration mới khi ứng dụng khởi động.

## Migration và data integrity

Migration nằm tại `src/main/resources/db/migration`:

- `V1__core_schema.sql`: core schema có FK, unique và check constraint.
- `V7__data_integrity_and_demo_cleanup.sql`: cleanup database legacy v1-v6 và thêm guardrail.

V7 thực hiện:

- xóa `TestGenre`, `New Genre` và chặn tạo lại ở DB/application;
- gộp genre trùng khác hoa-thường/khoảng trắng;
- unique `tmdb_id`, `imdb_id`, rating user/movie và watchlist user/movie;
- check rating 0.5–5.0, comment không rỗng;
- thêm index cho title, release year, movie genre, rating, watch history và search history;
- sửa metadata/genre của bốn record demo bị báo lỗi: The Big Green, King Kong vs. Godzilla, The Shawshank Redemption và The Matrix;
- thêm `metadata_source`, `metadata_verified_at` để truy vết metadata.

TMDB enrichment dùng `links.tmdb_id` làm khóa chính. Response bị bỏ qua nếu title không khớp hoặc release year lệch quá một năm, vì vậy external ID sai không thể ghi đè overview/poster của phim khác.

## Recommendation engine

Điểm cuối:

```text
hybrid = 0.40 × content + 0.40 × collaborative + 0.20 × popularity
```

- Content-based: tạo genre profile từ rating/lịch sử xem/preference và chấm candidate theo độ phù hợp.
- Collaborative: cosine similarity giữa vector rating, lấy các neighbor đủ số phim chung và dự đoán điểm có trọng số.
- Popularity: fallback theo dữ liệu xem/đánh giá toàn hệ thống.
- Exclusion rule: không gợi ý phim user đã xem hoặc đã rating.
- Cold start: preference nếu có; nếu chưa có dữ liệu thì dùng top-rated/trending.
- AI rerank: chỉ sắp xếp lại candidate do hybrid tạo; lỗi API sẽ fallback về hybrid.

Mỗi lần sinh gợi ý được lưu trong `user_recommendations` và `recommendation_logs`, gồm algorithm type, số phim, thời gian chạy và ghi chú fallback. UI hiển thị lý do theo genre/history hoặc lý do từ AI.

Các trọng số có thể đổi bằng properties:

```properties
recommendation.alpha=0.40
recommendation.beta=0.40
recommendation.gamma=0.20
```

## Business rules

| Nghiệp vụ | Rule |
|---|---|
| Rating | Một user/phim; rate lại là update; chỉ nhận bước 0.5 từ 0.5 đến 5.0 |
| Watchlist | Khóa chính `(user_id, movie_id)`, không duplicate |
| Comment/tag | Bắt buộc đăng nhập, không rỗng, giới hạn độ dài, output được escape |
| Recommendation | Không trả lại phim đã xem/đã rating |
| Genre | Trim/chuẩn hóa khoảng trắng, unique không phân biệt hoa-thường, cấm tên test |
| TMDB import | Upsert theo `tmdb_id`, kiểm tra title/year trước khi cập nhật |
| Admin | Mọi URL `/admin/**` yêu cầu `ROLE_ADMIN` ở backend |

## Bảo mật

- Password được hash bằng BCrypt.
- Session cookie + Spring Security filter chain; form POST dùng CSRF token.
- User ID cho rating/watchlist/comment lấy từ principal, không lấy từ request.
- OTP chỉ so khớp hash, có thời hạn và chỉ dùng một lần; không có mã bypass và không ghi OTP ra log.
- `REMEMBER_ME_KEY`, API key và database credential phải truyền qua environment variable.
- Demo seed mặc định tắt. Chỉ bật ở môi trường demo riêng bằng:

```env
DEMO_SEED_ENABLED=true
DEMO_PASSWORD=<a-demo-password-with-at-least-12-characters>
```

Không công khai mật khẩu demo trong repo.

## Kiểm thử

```bash
./gradlew test
```

Bộ test hiện kiểm tra context/schema trên database độc lập, security policy cho guest/user/admin và CSRF, hai user khác genre nhận hai kết quả recommendation khác nhau, AI fallback/chat orchestration, recommendation explanation, movie report, error template, TMDB title/year validation, TMDB upsert theo external ID, genre cleanup rule, latest release query và việc mã OTP cố định không thể bypass.

## Production environment

Các biến chính trên Railway:

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
PGHOST=${{Postgres.PGHOST}}
PGPORT=${{Postgres.PGPORT}}
PGDATABASE=${{Postgres.PGDATABASE}}
PGUSER=${{Postgres.PGUSER}}
PGPASSWORD=${{Postgres.PGPASSWORD}}
REMEMBER_ME_KEY=<random-secret-at-least-32-characters>
TMDB_API_KEY=<optional>
AI_API_KEY=<optional>
AI_BASE_URL=https://api.openai.com/v1
AI_CHAT_MODEL=gpt-4o-mini
DEMO_SEED_ENABLED=false
```

Nếu dùng Railway private network, không đặt `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`;
legacy environments có thể cần IPv6 để app kết nối được tới `*.railway.internal`.

Build image:

```bash
./gradlew clean build
docker build -t movierec .
```

## Demo bảo vệ đề xuất

1. Guest: Home → filter/search → movie detail → similar movies.
2. Action user: đăng nhập → rating/watchlist → For You → xem lý do gợi ý.
3. New user: chứng minh cold-start bằng trending/preference.
4. Admin: dashboard → genre/movie management → sync log.
5. Kỹ thuật: mở `RecommendationEngine`, migration V7 và test report để chứng minh thuật toán/data integrity.

## License

[MIT](LICENSE)
