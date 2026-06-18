# Nguyên Tắc Luồng Làm Việc Kĩ Thuật (Technical Workflow Rules)

Tài liệu này quy định các nguyên tắc bắt buộc đối với Agent/Developer trong quá trình tiếp nhận và xử lý yêu cầu kỹ thuật. Việc tuân thủ các quy định này là bắt buộc để đảm bảo chất lượng sản phẩm và tính nhất quán của hệ thống.

## 1. Nguyên Tắc Thiết Kế (Design Rules)
- **Tuân thủ 100%:** Mọi thành phần giao diện (UI), trải nghiệm người dùng (UX) và kiến trúc hệ thống phải tuân thủ tuyệt đối các quy tắc thiết kế đã đề ra. 
- Không tự ý thay đổi màu sắc, khoảng cách (spacing), font chữ hoặc logic luồng mà không có sự đồng ý từ cấp quản lý hoặc tài liệu đặc tả.

## 2. Quy Trình Luồng Làm Việc (Workflow)

### Bước 1: Nhận Yêu Cầu (Reception)
- Tiếp nhận thông tin từ người dùng/khách hàng. Đọc kỹ tất cả các tài liệu đính kèm và ghi chú liên quan.

### Bước 2: Phân Tích (Analysis)
- Nghiên cứu tính khả thi của yêu cầu.
- Đánh giá tác động của thay đổi đối với các module hiện tại của hệ thống.
- Xác định các rủi ro tiềm ẩn.

### Bước 3: Hỏi và Làm Rõ (Clarification)
- **Quy tắc 90%:** Ngay cả khi đã hiểu đến 90% nội dung yêu cầu, Agent **vẫn phải đặt câu hỏi** để làm rõ 10% còn lại hoặc xác nhận lại sự hiểu biết của mình.
- Tuyệt đối không được "giả định" ý định của người dùng. Mọi mơ hồ phải được giải quyết bằng giao tiếp.

### Bước 4: Xác Nhận (Confirmation)
- Sau khi đặt câu hỏi và nhận được phản hồi, tóm tắt lại giải pháp cuối cùng và đợi xác nhận từ người dùng trước khi bắt đầu viết code.

### Bước 5: Thực Hiện Code (Implementation)
- Tiến hành lập trình dựa trên phân tích và xác nhận đã có.
- Tuân thủ coding convention và các tiêu chuẩn sạch (Clean Code).

### Bước 6: Kiểm Lỗi (Debugging)
- Tự kiểm tra mã nguồn (Self-review).
- Chạy các test case để đảm bảo tính năng hoạt động đúng và không gây lỗi cho các phần khác.

### Bước 7: Build và Triển Khai (Deployment)
- Nếu có bất kỳ thay đổi nào về mã nguồn hoặc cấu hình:
    - Thực hiện **Run Build** cho phía **Server**.
    - Thực hiện **Run Build** cho phía **Client**.
- Kiểm tra tính ổn định sau khi build trên môi trường giả lập hoặc môi trường staging trước khi bàn giao.

---
*Lưu ý: Agent phải log lại trạng thái qua từng bước để đảm bảo tính minh bạch.*
