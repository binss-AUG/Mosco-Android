# Mosco - Dự án Gacha Vũ trụ Cao cấp

> [!IMPORTANT]
> ## 🚀 HƯỚNG DẪN KHỞI CHẠY TỰ ĐỘNG (DOCKER COMPOSER 1-CLICK)
> **Hệ thống đã được Container hóa toàn diện từ A đến Z. Dẹp bỏ hoàn toàn việc cài đặt thủ công Java hay MySQL trên máy khách!**
> 
> ### Yêu cầu duy nhất:
> Máy tính cần cài sẵn và đang bật ứng dụng **Docker Desktop**.
> 
> ### Các bước thực hiện:
> 1. Mở thư mục **`scripts/`**, đảm bảo có sẵn file dữ liệu **`dump.sql`** tại đây.
> 2. **Click đúp chuột** vào file **`run_setup.bat`** (Hệ thống sẽ tự động gọi PowerShell với quyền Admin).
> 3. Kịch bản sẽ tự động: Quét địa chỉ **IPv4 LAN** thực tế của Host để cấu hình kết nối API cho Android Client ➔ Tự động tải hình ảnh, biên dịch mã nguồn Backend từ A đến Z và dựng hình toàn bộ CSDL MySQL thông qua lệnh **`docker compose up -d`**!

Mosco là một ứng dụng di động mô phỏng game thẻ bài (Gacha) cao cấp, được xây dựng trên nền tảng Android Native với triết lý thiết kế "Quiet Luxury" và kiến trúc "Local-First". Dự án tập trung vào trải nghiệm người dùng mượt mà, giao diện mang phong cách vũ trụ (Galactic UI) và hệ thống quản lý tài nguyên tối ưu cho hàng vạn vật phẩm.

## 🌟 Điểm Nổi Bật của Dự án

### 1. Kiến trúc Tài nguyên Local-First (Offline Support)
Để đảm bảo tốc độ phản hồi tối ưu trên mọi dòng máy (kể cả giả lập Android 9):
- **Room Database:** Lưu trữ tạm thời siêu dữ liệu và cấu hình giao diện. Ngay khi mở app, dữ liệu hiển thị tức thì (Zero-latency) từ Local DB trước khi đồng bộ với Server.
- **Tối ưu Băng thông (OkHttp Interceptor):** Tự động ép kiểu nhận ảnh định dạng `WebP` từ Cloudflare (giảm 80% dung lượng mạng) khi load danh sách, chỉ tải ảnh gốc khi xem chi tiết.
- **Skeleton & Shimmer UI:** Mọi thao tác tải dữ liệu đều sử dụng Shimmer Animation, giúp UX luôn mượt mà.

### 2. Xử lý Thời gian thực & Tối ưu Giao tiếp (Real-time & Optimistic UI)
- **World Chat (WebSocket):** Hệ thống kênh chat toàn cầu tích hợp công nghệ **Optimistic UI**, tin nhắn được in ra màn hình ngay lập tức mà không có độ trễ mạng, kết hợp cơ chế Deduplication (khử trùng lặp) thông minh.
- **Debounce API:** Mọi nút bấm đều tích hợp cơ chế chống Spam click, bảo vệ Server khỏi các cuộc tấn công DDoS ngầm.

### 3. Hệ thống Gacha & Cốt lõi Backend
- **Pessimistic Locking (Khóa bi quan):** Thuật toán nâng cấp thẻ (giống FO4 mechanic) và giao dịch tài nguyên được đặt trong `@Transactional` và `@Lock(LockModeType.PESSIMISTIC_WRITE)` để chặn đứng 100% lỗi Race Condition hay Double-spending (tiêu tiền 2 lần).
- **Phân trang (Pagination) toàn diện:** Mọi API trả về danh sách thẻ bài đều bắt buộc dùng `Pageable` để tránh OOM (Out Of Memory) cho Client.
- **ETL (Extract, Transform, Load):** Background Jobs (`@Scheduled`) của Spring Boot chịu trách nhiệm cào dữ liệu thẻ bài mới từ các nguồn dữ liệu ngoài và đồng bộ (UPSERT) tự động vào cơ sở dữ liệu.

### 4. Hệ thống Trợ lý AI & Kiểm duyệt (RAG Architecture)
- **AI Moderation:** Trí tuệ nhân tạo kiểm duyệt nội dung chat theo thời gian thực (Real-time). Tự động cấm chat (Ban) nếu phát hiện ngôn từ độc hại qua 2 lớp Regex & Semantic Context.
- **Python Sidecar:** Một dịch vụ bổ trợ (FastAPI) để xử lý việc thu thập dữ liệu và xử lý ngôn ngữ tự nhiên.
- **Vector Store:** Lưu trữ kiến thức nghệ sĩ/thẻ bài dưới dạng Vector để truy xuất thông tin chính xác theo ngữ nghĩa.

## ⚙️ Công nghệ Sử dụng

- **Client:** Java Android Native, Retrofit 2, OkHttp, Glide, Lottie Animation, Room DB.
- **Server:** Java 21, Spring Boot 3.x, Spring Data JPA, Hibernate, MySQL 8.x, WebSocket.
- **AI / ETL:** Python FastAPI (Sidecar), Google Gemini API (LLM & Embeddings).
- **Thiết kế:** Modern Flat Design, Galactic UI, Typography 3 tầng (Pretendard, Orbitron, Chakra Petch).

## 📁 Cấu Trúc Dự án (Source Code)

- `client/app/src/main/java`: Mã nguồn ứng dụng Android (MVVM Pattern).
- `server/src/main/java`: Mã nguồn backend Spring Boot (MVC Pattern).
- `tools/rag_sidecar`: Dịch vụ bổ trợ AI xử lý dữ liệu NLP.
- `scripts/`: Các script tự động hóa cài đặt và triển khai.
- `raw-doc/`: Tài liệu dự án và báo cáo kết luận.

## 📜 Hướng Dẫn Cài Đặt (Manual)

Trường hợp không dùng script 1-click `run_setup.bat`, có thể chạy thủ công:

### Phía Cơ sở hạ tầng (Docker)
1. Cài đặt Docker Desktop.
2. Mở terminal tại thư mục gốc và chạy: `docker compose up -d` để khởi động MySQL và Redis.

### Phía Backend
1. Đảm bảo Java 21 đã được cài đặt.
2. Chạy lệnh `gradlew bootRun` trong thư mục `server/` để khởi động Spring Boot.

### Phía Client
1. Cập nhật địa chỉ IP của máy tính tại biến `BASE_URL` hoặc trong tệp `strings_config.xml`.
2. Mở thư mục `client/` bằng Android Studio và tiến hành Sync Gradle -> Run.

---
*Copyright © 2026 Mosco Project. Developed as a high-performance demonstration.*
