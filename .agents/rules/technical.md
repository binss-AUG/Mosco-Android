---
trigger: always_on
---

# Nguyên Tắc Luồng Làm Việc Kĩ Thuật (Technical Workflow Rules)

Tài liệu này quy định các nguyên tắc bắt buộc đối với Agent/Developer trong quá trình tiếp nhận và xử lý yêu cầu kỹ thuật, đặc biệt trong giai đoạn nước rút tối ưu hiệu năng của dự án Mosco.

## 1. Nguyên Tắc Thiết Kế (Design Rules)
- **Tuân thủ 100%:** Trải nghiệm người dùng (UX) phải đáp ứng chuẩn mượt mà trên Giả lập Android 9. Giao diện mặc định là Dark Mode. Mọi trạng thái chờ tải ảnh phải dùng Skeleton/Shimmer Loading.
- Không tự ý thay đổi cấu trúc bảng cơ sở dữ liệu (Schema) nếu không đảm bảo chuẩn 3NF và cơ chế Khóa ngoại (Foreign Key) cho 20.000+ thẻ bài.

## 2. Quy Trình Luồng Làm Việc (Workflow)

### Bước 1: Tiếp Nhận & Phân Tích (Analysis)
- Đọc kỹ yêu cầu, đối chiếu với giới hạn phần cứng (RAM giả lập, băng thông mạng) và giới hạn Server (Race condition, Deadlock).

### Bước 2: Hỏi và Làm Rõ (Clarification)
- **Quy tắc 90%:** Ngay cả khi đã hiểu đến 90% nội dung yêu cầu, Agent **VẪN PHẢI đặt câu hỏi** để làm rõ 10% còn lại. Tuyệt đối không "giả định" logic (ví dụ: tự ý sinh ra công thức đập thẻ mà không hỏi trước).

### Bước 3: Xác Nhận (Confirmation)
- Tóm tắt lại giải pháp kiến trúc ngắn gọn và đợi xác nhận từ người dùng trước khi bắt đầu sinh (generate) hàng loạt code.

### Bước 4: Thực Hiện Code (Implementation)
- Lập trình tuân thủ Clean Code, nguyên tắc DRY, và bám sát quy định ngôn ngữ (Java thuần cho Android, Tiếng Việt cho Comment).

### Bước 5: Kiểm Lỗi Nội Bộ (Debugging)
- Tự rà soát Memory Leak, NullPointerException, và kiểm tra xem Query có gây thắt cổ chai (N+1 query) hay không.

### Bước 6: Build và Triển Khai (Deployment)
- Nếu có can thiệp vào Dependencies hoặc Database:
    - Nhắc người dùng thực hiện **Run Build / Sync Gradle** cho Client.
    - Nhắc người dùng **Restart Application** cho Server Spring Boot.

---
*Lưu ý: Agent phải duy trì ngữ cảnh kỹ thuật nhất quán từ đầu đến cuối phiên làm việc, không được tự ý đề xuất các công nghệ ngoại lai vi phạm Golden Rules.*