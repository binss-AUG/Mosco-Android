# Danh Sách Tác Vụ: Mosco Dockerization

- [/] Thiết kế và Đóng gói Container hóa cho Backend
    - [x] Tạo tệp `server/Dockerfile` hỗ trợ Multi-stage build (tự động biên dịch Gradle từ A đến Z).
    - [x] Cập nhật `docker-compose.yml` để bọc service `backend` và `mysql` (loại bỏ `redis` theo yêu cầu).
    - [x] Cấu hình mount tự động nạp tệp `dump.sql` vào thư mục khởi tạo của MySQL.
- [/] Tối ưu hóa Kịch bản Triển khai (PowerShell)
    - [x] Gọt tỉa tệp `scripts/setup_mosco.ps1`, loại bỏ các bước cài đặt tệp tĩnh (JDK, MySQL MSI).
    - [x] Giữ nguyên vẹn logic quét địa chỉ IP LAN và tiêm tự động vào `AppConfig.java` của Client.
    - [x] Tích hợp lệnh tự động dựng hình và khởi chạy: `docker compose up --build -d`.
- [x] Cập nhật Tài liệu Dự án
    - [x] Viết lại hướng dẫn sử dụng trong `README.md` với kiến trúc 1-Click thông qua Docker Compose.
