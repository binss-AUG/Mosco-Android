# Báo Cáo Chiến Lược Chuyển Đổi Giao Diện (Refactor Strategy)
**Dự án:** Mosco (Hybrid Gacha & Collection App)
**Mục tiêu:** Tái cấu trúc (Refactor) hệ thống UI/UX từ phong cách "Game Vũ Trụ" sang "Ứng Dụng Ứng Dụng Thu Thập" chuyên nghiệp, đáp ứng tiêu chí Đồ án Di động và đảm bảo hiệu năng 60FPS trên Giả lập Android 9.

---

## 1. Bối cảnh và Thách thức hiện tại
- **Về mặt định hướng:** Ứng dụng đang bị "game hóa" quá đà, điều này có thể làm chệch hướng yêu cầu của một Đồ án Ứng dụng Di động thông thường. Cần một giao diện hiện đại, phẳng, nhưng vẫn giữ được chất riêng.
- **Về mặt kỹ thuật (Android 9):** Hiệu ứng "Liquid Glass" (Kính mờ, nhiễu hạt) dạng thời gian thực (Real-time Blur) ngốn rất nhiều RAM và CPU. API `RenderEffect` chỉ hỗ trợ tốt từ Android 12+. Việc ép Android 9 chạy Real-time Blur sẽ gây giật lag nghiêm trọng, vi phạm "Golden Rules" của dự án.
- **Về mặt Codebase:** Hệ thống Resouce (`colors.xml`, `dimens.xml`) và Component (XML, Java) đang phân mảnh, lặp code và rác (như đã phân tích ở các báo cáo trước).

## 2. Giải pháp Cốt lõi (Core Solutions)

### A. Chuyển đổi sang "Mosco Pure Material System"
- Thay thế hoàn toàn bảng màu Tím/Neon thành hệ màu **Slate & Blue** (Xanh dương doanh nghiệp kết hợp nền Xanh xám đen) dựa trên file Token JSON.
- Đưa toàn bộ font chữ về **Roboto** (Global Font Family) để tối ưu hóa triệt để tốc độ render text và loại bỏ gánh nặng load font tùy chỉnh.

### B. Giải pháp "Liquid Glass" tối ưu cho Android 9
Không sử dụng Real-time Blur (RenderScript). Thay vào đó, áp dụng **Static Liquid Glass**:
- Dùng mã màu HEX có độ trong suốt (Alpha) kết hợp với **Static Noise Bitmap** (Một file ảnh nhiễu hạt siêu nhẹ, lặp lại - tile mode).
- Sử dụng viền đứt nét (Dashed stroke) để tạo hiệu ứng khối nổi (Elevated) mà không cần dùng Shadow (bóng đổ) phần mềm tốn kém tài nguyên.

### C. Quy chuẩn Hình khối (Shape Standardization)
- **UI Elements (Nút bấm, Hộp thoại, Chip lọc):** Áp dụng triệt để độ bo góc `33px` (Pill shape) để tạo sự hiện đại, thân thiện, giống các siêu ứng dụng.
- **Objet Elements (Thẻ bài, Hình ảnh nhân vật):** Giữ nguyên độ bo góc mặc định (thường là 12dp - 16dp) hoặc vuông vắn. Tuyệt đối không can thiệp bo góc 33px vào Objet để tránh cắt xén artwork và giảm overhead cho CPU khi phải cắt (clip) ảnh.

## 3. Lộ trình Triển khai (Roadmap)
Việc dọn dẹp sẽ không đụng đến logic API hay Database, chỉ tập trung vào UI Layer theo 3 giai đoạn:

* **Phase 1: Token Injection (Ghi đè Tài nguyên)**
  - Xóa trắng `colors.xml`, `dimens.xml`, `colors_palette.xml` cũ.
  - Gen lại tài nguyên dựa trên `mosco_pure_roboto_tokens.json`.
  - Fix các lỗi "Resource not found" do đổi tên biến.

* **Phase 2: Layout Consolidation (Gom cụm XML)**
  - Tìm và áp dụng font Roboto cho toàn cục (xóa các khai báo font thừa).
  - Thay thế các màu cũ thành màu hệ thống mới.
  - Sửa đổi độ bo góc `33px` cho các thẻ `<MaterialButton>`, thẻ Dialog.

* **Phase 3: Java Cleanup (Dọn dẹp mã nguồn)**
  - Gỡ bỏ các hàm gọi Blur đắt đỏ trong `CardEffectHelper` (nếu có).
  - Đồng nhất `MoscoDialogHelper` sang một hệ thống Dialog mới gọn nhẹ hơn, dùng màu nền Slate và nút bấm xanh Blue.

---
**Kết luận:** Kế hoạch này là một bước lùi về độ "Màu mè" nhưng là **một bước tiến vĩ đại về "Kiến trúc và Hiệu năng"**. Nó hoàn toàn giải quyết được bài toán lạc đề của Đồ án và rào cản phần cứng của giả lập Android 9.
