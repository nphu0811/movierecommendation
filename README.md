# 🎬 MovieRec — AI Movie Recommendation System

**Hệ thống gợi ý phim thông minh** sử dụng **hybrid algorithm** (Content-based + Collaborative Filtering + Popularity) tích hợp **OpenAI GPT-3.5** để cung cấp trải nghiệm cá nhân hóa.

<div align="center">

[![Live Demo](https://img.shields.io/badge/🚀%20Live%20Demo-Railway-blue?style=for-the-badge)](https://movierecommendation-production-6e68.up.railway.app/)
[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=java)](https://www.oracle.com/java/technologies/downloads/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-green?style=flat-square&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

</div>

---

## 📋 Mục Lục

- [✨ Tính Năng Chính](#-tính-năng-chính)
- [🛠️ Tech Stack](#️-tech-stack)
- [⚙️ Cài Đặt](#️-cài-đặt)
- [🚀 Deploy](#-deploy)
- [📁 Cấu Trúc Project](#-cấu-trúc-project)
- [👥 Team Workflow](#-team-workflow)
- [📞 Hỗ Trợ](#-hỗ-trợ)

---

## ✨ Tính Năng Chính

### 🤖 AI & Recommendation Engine
- **Hybrid Recommendation**: Kết hợp Content-based + Collaborative Filtering + Popularity
- **GPT-3.5 Integration**: Gợi ý phim thông minh dựa trên ngữ cảnh
- **Real-time Search**: Tìm kiếm phim với autocomplete tức thì

### 🎥 User Experience
- 🎬 Xem trailer YouTube trực tiếp trong ứng dụng
- 🔗 Links nhanh đến Netflix / FPT Play
- ⭐ Đánh giá & bình luận phim
- 📌 Watchlist & lịch sử xem
- 💾 Lưu trữ các phim yêu thích

### 🔧 Admin Features
- 📸 Tự động fetch poster từ TMDB API
- 🌱 Seed dữ liệu ratings & comments
- 📊 Dashboard quản lý nội dung
- 👤 Quản lý người dùng

---

## 🛠️ Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Backend** | Java, Spring Boot | 21 / 3.4.5 |
| **Security** | Spring Security, BCrypt, JWT | Latest |
| **Frontend** | Thymeleaf, HTML5, CSS3 | Netflix-style |
| **Database** | PostgreSQL (Prod) / SQL Server (Local) | 14+ |
| **AI/ML** | OpenAI GPT-3.5-turbo | Latest |
| **External APIs** | TMDB API, MovieLens 100K | - |
| **Build Tool** | Gradle | 8.x |
| **Deployment** | Railway, Docker | Latest |

---

## ⚙️ Cài Đặt

### 📋 Yêu Cầu
- Java 21+
- PostgreSQL 14+ (hoặc SQL Server cho local)
- Gradle 8.x
- Git

### 1️⃣ Clone Repository

```bash
git clone https://github.com/nphu0811/movierecommendation.git
cd movierecommendation
```

### 2️⃣ Cấu Hình Database

#### Local (SQL Server)
```bash
# Chạy file setup
sqlcmd -S localhost -U sa -P <password> -i setup_full.sql
```

#### Production (PostgreSQL)
Liên hệ team lead để nhận file `setup_full.sql`:
```bash
psql -U postgres -d movierecommendation -f setup_full.sql
```

### 3️⃣ Tạo File Configuration

```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```

**Điền các giá trị sau vào `application.properties`:**

```properties
# OpenAI Configuration
openai.api.key=sk-proj-YOUR_OPENAI_KEY

# TMDB API Configuration
tmdb.api.key=YOUR_TMDB_API_KEY

# Security
app.remember-me-key=YOUR_RANDOM_32_CHARACTER_STRING

# Database (Local)
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=movierecommendation
spring.datasource.username=sa
spring.datasource.password=your_password

# Server
server.port=8080
```

**Cách lấy API Keys:**
- 🔑 **OpenAI**: https://platform.openai.com/account/api-keys
- 🎬 **TMDB**: https://www.themoviedb.org/settings/api

### 4️⃣ Chạy Application

```bash
# Build & Run
./gradlew bootRun

# Hoặc build JAR
./gradlew build
java -jar build/libs/movierecommendation-*.jar
```

Truy cập tại: **http://localhost:8080**

### 👤 Tài Khoản Test hệ thống

#### Tài Khoản Admin
```
Email:    admin@movierec.com
Password: (liên hệ team lead)
```

#### Tài Khoản Demo Người dùng (Phase 2 Seeded)
Dưới đây là 3 tài khoản demo được tự động seed khi khởi động hệ thống để kiểm thử sự khác biệt trong thuật toán gợi ý phim:

1. **User thích Hành động / Phiêu lưu (Action/Adventure)**
   - Email: `action.demo@example.com`
   - Mật khẩu: `123456`
   - Trải nghiệm: Gợi ý các phim thiên về Hành động và lý do hiển thị theo gu Hành động.

2. **User thích Hài hước / Lãng mạn (Comedy/Romance)**
   - Email: `comedy.demo@example.com`
   - Mật khẩu: `123456`
   - Trải nghiệm: Gợi ý các phim thiên về Hài hước, Lãng mạn.

3. **User mới tinh (New user - Cold Start)**
   - Email: `new.demo@example.com`
   - Mật khẩu: `123456`
   - Trải nghiệm: Nhận gợi ý phổ biến (trending) hoặc theo thiết lập sở thích Preferences.

---

## 🚀 Deploy

### Railway (Automatic Deployment)

Railway tự động deploy khi push code lên nhánh `main`.

#### 📌 Cấu Hình Environment Variables

Vào [Railway Dashboard](https://railway.app) và set các biến sau:

```env
# Database
DATABASE_URL=postgresql://user:password@host:5432/movierecommendation

# AI & APIs
OPENAI_API_KEY=sk-proj-...
TMDB_API_KEY=f3e922...

# Security
REMEMBER_ME_KEY=your_random_secret_key

# Server
JAVA_OPTS=-Xmx512m -Xms256m
```

#### 🔄 Deploy Flow

```bash
# 1. Commit code
git add .
git commit -m "✨ Add feature X"

# 2. Push lên main
git push origin main

# 3. Railway tự động deploy
# ✅ Check Railway dashboard để xem trạng thái
```

#### 🔗 Live URL
🎯 **https://movierecommendation-production-6e68.up.railway.app/**

---

## 📁 Cấu Trúc Project

```
movierecommendation/
├── src/main/java/com/movierec/
│   ├── algorithm/           # 🤖 Hybrid recommendation engine
│   │   ├── ContentBased.java
│   │   ├── CollaborativeFiltering.java
│   │   └── HybridRecommender.java
│   ├── config/              # ⚙️ Security, WebClient config
│   │   ├── SecurityConfig.java
│   │   └── WebClientConfig.java
│   ├── controller/          # 🌐 HTTP endpoints (REST API)
│   │   ├── MovieController.java
│   │   ├── UserController.java
│   │   └── AdminController.java
│   ├── entity/              # 🗄️ JPA entities (Database models)
│   │   ├── Movie.java
│   │   ├── User.java
│   │   ├── Rating.java
│   │   └── Comment.java
│   ├── repository/          # 🔍 Database queries (Spring Data JPA)
│   │   ├── MovieRepository.java
│   │   ├── UserRepository.java
│   │   └── RatingRepository.java
│   ├── service/             # 💼 Business logic, OpenAI, TMDB integration
│   │   ├── MovieService.java
│   │   ├── RecommendationService.java
│   │   ├── OpenAIService.java
│   │   └── TMDBService.java
│   └── MovieRecommendationApplication.java
├── src/main/resources/
│   ├── application.properties.example
│   ├── templates/           # 🎨 Thymeleaf HTML templates
│   │   ├── home.html
│   │   ├── movie-detail.html
│   │   └── admin-dashboard.html
│   └── static/
│       ├── css/             # Netflix-style styling
│       ├── js/              # Frontend logic
│       └── images/
├── src/test/
│   ├── java/                # 🧪 Unit tests
│   └── resources/
├── build.gradle             # Gradle build configuration
├── Dockerfile               # Docker image definition
├── docker-compose.yml       # Local development with Docker
└── README.md
```

---

## 👥 Team Workflow

### 🔄 Git Flow

```bash
# 1️⃣ Bước 1: Update code mới nhất
git pull origin main

# 2️⃣ Bước 2: Tạo feature branch (tuỳ chọn)
git checkout -b feature/your-feature-name

# 3️⃣ Bước 3: Code & Commit
git add .
git commit -m "✨ Your feature description"

# 4️⃣ Bước 4: Push & Create Pull Request
git push origin feature/your-feature-name
# Vào GitHub tạo Pull Request

# 5️⃣ Bước 5: Merge sau khi approved
git checkout main
git pull origin main
git merge feature/your-feature-name
git push origin main
```

### 💡 Commit Message Convention

```
✨ Thêm feature mới
🐛 Fix bug
📖 Update documentation
🎨 Refactor code
🧪 Add tests
⚡ Improve performance
🔒 Security fix
```

### 🤝 Code Review Checklist
- [ ] Code theo style guide của team
- [ ] Có unit tests
- [ ] Không có magic numbers
- [ ] Error handling hoàn thiện
- [ ] Documentation cập nhật

---

## 🏗️ Architecture & Recommendation Flow

Hệ thống sử dụng cơ chế gợi ý lai (Hybrid Recommendation) kết hợp giữa các thuật toán lọc cộng tác (Collaborative Filtering), lọc dựa trên nội dung nâng cao (Content-Based Filtering) và độ phổ biến hệ thống (Popularity Score). 

### Luồng xử lý thuật toán (Recommendation Flow)

```
[Rating + Lịch sử xem + Thể loại ưu thích + Độ phổ biến]
                       ↓
         ┌─────────────┼─────────────┐
         ↓             ↓             ↓
  [Content-based] [Collaborative] [Popularity]
    Score (40%)     Score (40%)   Score (20%)
         └─────────────┼─────────────┘
                       ↓
                [Hybrid Score]
                       ↓
           [Optional AI Chat Rerank]
                       ↓
          [Personalized Explanations]
```

1. **Thu thập dữ liệu đầu vào**: Lịch sử đánh giá phim (ratings), lịch sử xem phim (watch history) và các tùy chọn sở thích (User Preferences) của người dùng.
2. **Lọc dựa trên nội dung (Content-Based Score)**: 
   - Xây dựng hồ sơ thể loại (Genre Profile) dựa trên các phim được đánh giá cao. Nếu lịch sử trống, hệ thống sẽ sử dụng thể loại yêu thích được khai báo trong `User Preferences` (để giải quyết bài toán khởi động lạnh - cold start).
   - Lọc bỏ các thể loại nằm trong danh sách không thích (disliked genres).
   - Tính toán độ tương tự tương đồng (Cosine/Jaccard Similarity) của phim dựa trên 4 yếu tố trọng số:
     - Thể loại trùng lặp (Genre Overlap): 50%
     - Tags trùng lặp (Tag Overlap): 20%
     - Từ khóa nội dung (Description keyword overlap): 20%
     - Đạo diễn & Diễn viên trùng lặp (Actor/Director overlap): 10%
3. **Lọc cộng tác (Collaborative Filtering)**:
   - Tìm kiếm các người dùng láng giềng tương đồng (k-Nearest Neighbors) có chung danh sách phim đã đánh giá.
   - Tính Cosine Similarity giữa các vector rating của người dùng mục tiêu với các láng giềng.
   - Dự đoán điểm đánh giá cho các phim chưa xem dựa trên điểm số của láng giềng có trọng số similarity.
4. **Điểm phổ biến (Popularity Score)**:
   - Tính toán điểm phổ biến dựa trên tổng lượt xem (rating count) của phim trên toàn hệ thống.
5. **Điểm lai Hybrid (Hybrid Score)**:
   - Điểm số cuối cùng = `alpha * ContentScore` + `beta * CollabScore` + `gamma * PopularityScore` (mặc định: 40% Content + 40% Collab + 20% Popularity).
6. **AI Reranking (Tùy chọn)**:
   - Khi sử dụng AI Chatbot, danh sách các phim phù hợp nhất sẽ được chuyển làm candidate gửi tới OpenAI GPT kèm theo ngữ cảnh sở thích của người dùng để chọn lọc, sắp xếp lại và đưa ra lý giải bằng ngôn ngữ tự nhiên.

### Security Flow

```
User Login
    ↓
Spring Security Filter Chain
    ↓
JWT Token / Session
    ↓
Role-Based Access Control (RBAC)
    ↓
Encrypted Password (BCrypt)
```

---

## 📞 Hỗ Trợ

### 🐛 Report Bugs
1. Mô tả chi tiết lỗi
2. Steps to reproduce
3. Expected vs Actual behavior
4. Attach screenshots nếu có

**Liên hệ:** team lead hoặc tạo Issue trên GitHub

### 📚 Documentation
- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [TMDB API](https://developer.themoviedb.org/docs)
- [OpenAI API](https://platform.openai.com/docs)

### 🔐 Security Issues
⚠️ **Không công khai bug bảo mật!** Liên hệ team lead trực tiếp.

---

## 📄 License

MIT License - Xem file [LICENSE](LICENSE) để chi tiết.

---

## 👨‍💻 Contributors

| Role | Contact |
|------|---------|
| Team Lead & Backend |  [@Yuhnart-07](https://github.com/Yuhnart-07) |
| FullStack |[@nphu0811](https://github.com/nphu0811) |

---

<div align="center">

**Made with ❤️ by MovieRec Team**

⭐ Nếu project hữu ích, hãy cho một star!

</div>
