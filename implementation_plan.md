# Kế Hoạch Chuyển Đổi Kiến Trúc Triển Khai: Mosco Dockerization

Tài liệu này vạch ra lộ trình nâng cấp quy trình triển khai Backend của dự án Mosco sang nền tảng Container hóa với **Docker Compose**, nhằm chấm dứt triệt để các rào cản về tương thích môi trường (Java version, MySQL Service, và tường lửa tải file).

## User Review Required

> [!IMPORTANT]
> **Thay đổi lớn về vận hành:** Việc áp dụng Docker Compose sẽ thay thế hoàn toàn bộ công cụ cài đặt dựa trên PowerShell cũ. Máy khách (Host) sẽ không cần cài trực tiếp JDK hay MySQL Server nữa, mà chỉ yêu cầu duy nhất ứng dụng **Docker Desktop** đang hoạt động.

## Open Questions

> [!TIP]
> 1. Chuỗi kết nối mạng LAN từ Android Client vẫn cần trỏ đến địa chỉ IP nội bộ của Host (Ví dụ: `http://192.168.1.x:8080/`). Chúng ta có nên tạo một kịch bản siêu nhẹ `start_mosco.ps1` chỉ để quét IP tự động ghi vào `AppConfig.java` rồi tự gọi `docker compose up -d` không?
> 2. Có muốn đóng gói cả bước biên dịch Gradle (`./gradlew bootJar`) vào trong quá trình dựng hình (Multi-stage build) của Dockerfile để máy khách không cần tự chạy lệnh build trước không? (Khuyến nghị: Có, để đạt chuẩn 100% tự động hóa).

## Proposed Changes

### Kịch Bản Lỗi Thời (Scripts)

Loại bỏ các tệp kịch bản cài đặt tốn kém và dễ văng lỗi do phụ thuộc vào hệ điều hành Windows gốc.

#### [DELETE] [setup_mosco.ps1](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/scripts/setup_mosco.ps1)
#### [DELETE] [run_setup.bat](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/scripts/run_setup.bat)

### Cấu Hình Dockerization

Đóng gói Backend Spring Boot và cấu hình các dịch vụ đệm (MySQL, Redis) thành một hệ sinh thái nội bộ đồng nhất.

#### [NEW] [Dockerfile](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/Dockerfile)
- Sử dụng hình ảnh cơ sở `eclipse-temurin:21-jdk-alpine` (hoặc multi-stage build để tự động gọi Gradle build).
- Khởi chạy tệp `.jar` đã biên dịch tại cổng `8080`.

#### [MODIFY] [docker-compose.yml](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/docker-compose.yml)
- Bổ sung service `backend` phụ thuộc vào `mysql` và `redis`.
- Tự động gắn (mount) tệp `dump.sql` vào điểm neo `/docker-entrypoint-initdb.d/` của container MySQL để nạp dữ liệu tự động ngay khi khởi tạo CSDL.
- Khai báo các biến môi trường cấu hình liên kết mạng nội bộ (`DB_HOST: mysql`, `REDIS_HOST: redis`).

### Tài Liệu Dự Án

Cập nhật lại tài liệu hướng dẫn đầu vào để người dùng dễ dàng nắm bắt cách thức khởi chạy mới.

#### [MODIFY] [README.md](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/README.md)
- Viết lại toàn bộ mục Hướng dẫn Cài đặt & Vận hành bằng 1 lệnh duy nhất: `docker compose up -d`.

## Verification Plan

### Automated Tests
- Dựng toàn bộ stack dịch vụ thông qua lệnh: `docker compose up --build -d`
- Kiểm tra trạng thái sức khỏe của các container: `docker compose ps`
- Rà soát nhật ký khởi chạy Backend để đảm bảo kết nối thành công tới CSDL và Redis: `docker compose logs -f backend`

### Manual Verification
- Truy cập từ trình duyệt Host vào địa chỉ `http://localhost:8080/` để xác nhận máy chủ phản hồi.
- Kiểm tra ứng dụng Android Client trên Giả lập kết nối thành công tới Backend.
