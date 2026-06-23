# Mosco - Dự án Gacha Vũ trụ Cao cấp

Mosco là một ứng dụng di động mô phỏng game thẻ bài (Gacha) cao cấp, được xây dựng trên nền tảng Android Native với triết lý thiết kế "Quiet Luxury" và kiến trúc "Local-First". Dự án tập trung vào trải nghiệm người dùng mượt mà, giao diện mang phong cách vũ trụ (Galactic UI) và hệ thống quản lý tài nguyên tối ưu cho hàng vạn vật phẩm.

## Hướng Dẫn Cài Đặt và Khởi Chạy

Dự án hỗ trợ hai phương pháp khởi chạy: Tự động qua Docker (khuyến nghị) và Chạy thủ công.

### Phương Pháp 1: Khởi Chạy Tự Động (Docker Compose 1-Click)
Hệ thống đã được Container hóa toàn diện, giúp bạn không cần cài đặt thủ công Java hay MySQL trên máy khách.

**Yêu cầu:** Máy tính cần cài sẵn và đang bật ứng dụng Docker Desktop.

**Các bước thực hiện:**
1. Mở thư mục `scripts/`.
2. Chạy file `run_setup.bat` (Hệ thống sẽ tự động gọi PowerShell với quyền Admin).
3. Kịch bản sẽ tự động tải các thư viện cần thiết, biên dịch mã nguồn Backend và khởi tạo toàn bộ cơ sở dữ liệu MySQL thông qua lệnh `docker compose up -d`.
4. Khởi động Ngrok bằng lệnh: `ngrok http --domain=seldom-oozy-angelfish.ngrok-free.dev 8080` để public Server.
5. Mở thư mục `client/` bằng Android Studio và bấm **Run**. App đã được cấu hình tự động trỏ đến tên miền tĩnh Ngrok nên sẽ kết nối thành công bất kể bạn đang dùng mạng nào.

### Phương Pháp 2: Khởi Chạy Thủ Công
Trường hợp không dùng script tự động, bạn có thể chạy thủ công theo các bước sau:

**Phía Cơ sở hạ tầng (Docker)**
1. Cài đặt Docker Desktop.
2. Mở terminal tại thư mục gốc dự án và chạy lệnh: `docker compose up -d` để khởi động MySQL.

**Phía Backend**
1. Đảm bảo Java 21 đã được cài đặt trên máy.
2. Mở terminal tại thư mục `server/` và chạy lệnh: `gradlew bootRun` (hoặc `.\gradlew bootRun` trên Windows) để khởi động Spring Boot.

**Phía Client**
1. Mở terminal và chạy lệnh `ngrok http --domain=seldom-oozy-angelfish.ngrok-free.dev 8080` (Cần cài đặt Ngrok trước).
2. Mở thư mục `client/` bằng Android Studio, đợi đồng bộ Gradle (Sync) và bấm **Run**. Không cần phải cấu hình lại IP LAN.

## Tài Khoản Kiểm Thử

Để thuận tiện cho việc đánh giá hoặc thử nghiệm, hệ thống tự động tạo sẵn tài khoản Admin:
- **Tên đăng nhập:** `admin`
- **Mật khẩu:** `admin123`

*Lưu ý:* Khi khởi động Server, hệ thống sẽ tự động tạo bảng (Auto-DDL) và nạp dữ liệu thẻ bài. Tài khoản Admin được cung cấp sẵn lượng lớn tài nguyên (1 Tỷ Vàng, 1 Tỷ Kim cương, x999 Gói Thẻ) và mở khóa tất cả các thẻ bài ở Level 1 để thử nghiệm tính năng Nâng cấp/Gacha ngay lập tức.

## Điểm Nổi Bật của Dự án

### 1. Kiến trúc Tài nguyên Local-First (Offline Support)
Để đảm bảo tốc độ phản hồi tối ưu trên mọi dòng máy:
- **Room Database:** Lưu trữ tạm thời siêu dữ liệu và cấu hình giao diện. Ngay khi mở app, dữ liệu hiển thị tức thì từ Local DB trước khi đồng bộ với Server.
- **Tối ưu Băng thông (OkHttp Interceptor):** Tự động ép kiểu nhận ảnh định dạng WebP từ Cloudflare (giảm 80% dung lượng) khi tải danh sách, chỉ tải ảnh gốc khi xem chi tiết.
- **Skeleton & Shimmer UI:** Mọi thao tác tải dữ liệu đều sử dụng Shimmer Animation, giúp trải nghiệm người dùng luôn mượt mà.

### 2. Xử lý Thời gian thực & Tối ưu Giao tiếp (Real-time & Optimistic UI)
- **World Chat (WebSocket):** Hệ thống kênh chat toàn cầu tích hợp công nghệ Optimistic UI. Tin nhắn hiển thị ngay lập tức mà không có độ trễ, kết hợp cơ chế khử trùng lặp (Deduplication) thông minh.
- **Debounce API:** Mọi nút bấm đều tích hợp cơ chế chống spam, bảo vệ Server khỏi các cuộc tấn công dạng từ chối dịch vụ.

### 3. Hệ thống Gacha & Cốt lõi Backend
- **Khóa bi quan (Pessimistic Locking):** Thuật toán nâng cấp thẻ và giao dịch tài nguyên được đặt trong `@Transactional` và `@Lock(LockModeType.PESSIMISTIC_WRITE)` để ngăn chặn hoàn toàn lỗi Race Condition hay Double-spending (tiêu tiền hai lần).
- **Phân trang (Pagination) toàn diện:** Các API trả về danh sách thẻ bài đều bắt buộc dùng Pageable để tránh lỗi hết bộ nhớ (OOM) cho Client.
- **ETL (Extract, Transform, Load):** Background Jobs (`@Scheduled`) của Spring Boot đảm nhận việc thu thập dữ liệu thẻ bài mới từ nguồn ngoài và tự động đồng bộ (UPSERT) vào cơ sở dữ liệu.

### 4. Hệ thống Trợ lý AI & Kiểm duyệt (RAG Architecture)
- **AI Moderation:** Trí tuệ nhân tạo kiểm duyệt nội dung chat theo thời gian thực. Tự động cấm chat nếu phát hiện ngôn từ độc hại qua hai lớp Regex và Semantic Context.
- **Python Sidecar:** Một dịch vụ bổ trợ (FastAPI) để thu thập dữ liệu và xử lý ngôn ngữ tự nhiên.
- **Vector Store:** Lưu trữ kiến thức về nghệ sĩ và thẻ bài dưới dạng Vector để truy xuất thông tin chính xác theo ngữ nghĩa.

## Công nghệ Sử dụng

- **Client:** Java Android Native, Retrofit 2, OkHttp, Glide, Lottie Animation, Room DB.
- **Server:** Java 21, Spring Boot 3.x, Spring Data JPA, Hibernate, MySQL 8.x, WebSocket.
- **AI / ETL:** Python FastAPI (Sidecar), Google Gemini API (LLM & Embeddings).
- **Thiết kế:** Modern Flat Design, Galactic UI, Typography 3 tầng (Pretendard, Orbitron, Chakra Petch).

## Cấu Trúc Dự án (Source Code)

- `client/app/src/main/java`: Mã nguồn ứng dụng Android (MVVM Pattern).
- `server/src/main/java`: Mã nguồn backend Spring Boot (MVC Pattern).
- `tools/rag_sidecar`: Dịch vụ bổ trợ AI xử lý dữ liệu NLP.
- `scripts/`: Các kịch bản tự động hóa cài đặt và triển khai.
- `raw-doc/`: Tài liệu dự án và báo cáo kết luận.

