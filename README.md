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

Mosco là một ứng dụng di động mô phỏng game thẻ bài (Gacha) cao cấp, được xây dựng trên nền tảng Android Native với triết lý thiết kế "Quiet Luxury" và kiến trúc "Local-First". Dự án tập trung vào trải nghiệm người dùng mượt mà, giao diện mang phong cách vũ trụ (Galactic UI) và hệ thống quản lý tài nguyên tối ưu.

## Điểm Nổi Bật của Dự án

### 1. Hệ thống Typography Chuẩn hóa
Dự án sử dụng hệ thống phông chữ đa tầng để phân tách rõ rệt vai trò của từng loại thông tin:
- Mosco Luxury (Pretendard): Sử dụng cho toàn bộ phần thân văn bản, mô tả và các nhãn điều hướng. Mang lại cảm giác hiện đại và dễ đọc.
- Mosco Galactic (Orbitron): Dành riêng cho các tiêu đề module và các thành phần mang tính thương hiệu, tạo phong cách tương lai.
- Mosco Technical (Chakra Petch): Sử dụng cho các con số kỹ thuật, chỉ số thẻ bài (OVR, Level, HP, ATK) để tạo sự chính xác và chuyên nghiệp.

### 2. Kiến trúc Tài nguyên Local-First
Để đảm bảo tốc độ phản hồi tối ưu, Mosco triển khai kiến trúc tài nguyên thông minh:
- Tải dữ liệu đa luồng: Sử dụng hệ thống quản lý luồng (CardAssetManager) để tải tài nguyên từ server ngay khi khởi động.
- Caching Hybrid: Kết hợp lưu trữ tạm thời và bộ nhớ cục bộ để hiển thị hình ảnh tức thì.
- Tối ưu hóa hiệu năng: Xử lý sắp xếp và lọc dữ liệu ở luồng nền để đảm bảo giao diện mượt mà.
- Kiểm soát tốc độ cuộn: Hệ thống giới hạn tốc độ cuộn (ViewUtils) giúp quản lý việc render hình ảnh đồng thời, tránh quá tải bộ nhớ.

### 3. Hệ thống Trợ lý AI (RAG Architecture)
Điểm đặc biệt của dự án là việc tích hợp mô hình ngôn ngữ lớn (LLM) với kỹ thuật RAG (Retrieval-Augmented Generation):
- Trợ lý thông minh: AI có khả năng trả lời về kiến thức nghệ sĩ và hướng dẫn sử dụng các chức năng trong ứng dụng.
- Python Sidecar: Sử dụng một dịch vụ bổ trợ (FastAPI) để xử lý việc thu thập dữ liệu (Scraping) và xử lý ngôn ngữ tự nhiên.
- Vector Store: Lưu trữ kiến thức dưới dạng Vector để truy xuất thông tin chính xác theo ngữ nghĩa.

### 4. Tính năng Hệ thống
- Hệ thống Gacha: Thuật toán ngẫu nhiên dựa trên lý thuyết hỗn loạn (Chaos Theory) đảm bảo tính minh bạch.
- Nâng cấp thẻ bài: Cơ chế nâng cấp vật phẩm với tỷ lệ thành công được tính toán dựa trên cấp độ và số lượng phôi sử dụng.
- Đội hình Cộng hưởng: Hệ thống tự động tính toán điểm thưởng dựa trên sự kết hợp giữa các thẻ bài trong đội hình.
- Chụp ảnh mô phỏng (Overlay): Cho phép người dùng tương tác với thẻ bài trong không gian camera của thiết bị.

## Công nghệ Sử dụng

- Client: Java Android Native, Retrofit 2, OkHttp, Glide, Lottie Animation, Google ML Kit.
- Server: Java 21, Spring Boot 3.x, Spring Data JPA, MySQL 8.x.
- AI: Python FastAPI (Sidecar), Google Gemini API (LLM & Embeddings).
- Thiết kế: Modern Flat Design, Glassmorphism nhẹ.

## Cấu Trúc Dự án

- client/app/src/main/java: Mã nguồn ứng dụng Android.
- server/src/main/java: Mã nguồn backend Spring Boot.
- tools/rag_sidecar: Dịch vụ bổ trợ AI xử lý dữ liệu.
- res/values/: Hệ thống tài nguyên (colors, strings, dimens, styles).

## Hướng Dẫn Cài Đặt

### Phía Cơ sở hạ tầng (Docker)
1. Cài đặt Docker Desktop trên Windows.
2. Mở terminal tại thư mục gốc và chạy: `docker-compose up -d`.
   * Lệnh này sẽ khởi động MySQL (port 3306) và Redis (port 6379).

### Phía Backend
1. Cấu hình thông số MySQL trong file `application.properties` hoặc `.env`.
2. Chạy lệnh `gradlew bootRun` để khởi động server (mặc định port 8080).

### Phía Client
1. Cập nhật địa chỉ IP của server trong `strings_config.xml`.
2. Build ứng dụng bằng Android Studio.

## Quy Tắc Phát Triển

- Ngôn ngữ mã nguồn: Sử dụng duy nhất ngôn ngữ Java cho phía Client.
- Ghi chú: Ghi chú bằng tiếng Việt để giải thích lý do (WHY) thực hiện logic.
- Commit: Tuân thủ định dạng type(scope): description (ví dụ: style(ui): update typography styles).

Copyright 2026 Mosco Project.
