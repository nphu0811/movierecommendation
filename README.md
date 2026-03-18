# 🎬 MovieRec — AI Movie Recommendation System

Web app gợi ý phim thông minh dùng thuật toán hybrid (Content-based + Collaborative Filtering + Popularity) tích hợp OpenAI GPT-3.5.

## Link railway : https://movierecommendation-production-6e68.up.railway.app/

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4.5 |
| Security | Spring Security, BCrypt |
| Frontend | Thymeleaf, CSS (Netflix-style) |
| Database | SQL Server (local) / PostgreSQL (production) |
| AI | OpenAI GPT-3.5-turbo |
| Data | TMDB API, MovieLens 100K dataset |
| Deploy | Railway (Docker) |

## Tính năng

- Hybrid recommendation: Content-based + Collaborative + Popularity
- AI recommendations via OpenAI GPT
- Tìm kiếm phim real-time với autocomplete
- Xem trailer YouTube ngay trong trang
- Netflix / FPT Play links
- Admin dashboard: fetch posters TMDB, seed ratings/comments
- Rating, watchlist, watch history, comments

## Setup local

### 1. Clone repo
```bash
git clone https://github.com/nphu0811/movierecommendation.git
cd movierecommendation
```

### 2. Tạo database
Chạy file `setup_full.sql` trong PotgressSQL (liên hệ team lead để lấy file).

### 3. Tạo application.properties
```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```
Điền vào:
```properties
openai.api.key=YOUR_OPENAI_KEY
tmdb.api.key=YOUR_TMDB_KEY
app.remember-me-key=ANY_RANDOM_32_CHAR_STRING
```

### 4. Chạy
```bash
./gradlew bootRun
```
Truy cập: http://localhost:8080

**Admin:** `admin@movierec.com` (password: liên hệ team lead)

## Deploy (Railway)

Railway tự động deploy khi push lên `main`.

Cần set các env vars trong Railway dashboard:
```
DATABASE_URL     = (Railway tự inject khi thêm PostgreSQL service)
OPENAI_API_KEY   = sk-proj-...
TMDB_API_KEY     = f3e922...
REMEMBER_ME_KEY  = any_random_secret
```

## Team workflow

```bash
# Trước khi code
git pull origin main

# Sau khi sửa
git add .
git commit -m "Mô tả thay đổi"
git push origin main
```

## Cấu trúc project

```
src/main/java/.../
├── algorithm/        # Hybrid recommendation engine
├── config/           # Security, WebClient config
├── controller/       # HTTP endpoints
├── entity/           # JPA entities
├── repository/       # Database queries
└── service/          # Business logic, OpenAI, TMDB
```
