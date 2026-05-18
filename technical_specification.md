# Đặc Tả Kỹ Thuật: Mosco Pure Material System

Tài liệu này định nghĩa các tiêu chuẩn kỹ thuật giao diện cho dự án Mosco (Phiên bản Hybrid App 4.0.0), được dịch trực tiếp từ bộ mã `mosco_pure_roboto_tokens.json`.

---

## 1. Thông Tin Hệ Thống (System Meta)
- **Framework:** Mosco Pure Material System
- **Kiến trúc:** Single Font Family Native Design (High-Performance DRY). Ưu tiên tốc độ khung hình (FPS) và tiết kiệm RAM tối đa trên hệ thống máy ảo Android 9.
- **Ngôn ngữ thiết kế:** Phẳng (Flat) kết hợp Static Liquid Glass (Kính mờ tĩnh).

---

## 2. Hệ Thống Màu Sắc (Color Palette)
Hệ thống loại bỏ hoàn toàn dải màu Tím Neon/Không gian, chuyển sang tông Xanh dương - Xám đen chuyên nghiệp (Dark Theme mặc định).

### Nhóm Brand (Thương hiệu)
Màu sắc nhận diện, dùng cho Nút bấm chính, Icon kích hoạt, và các điểm nhấn (Accent).
- **Primary:** `#24A1DE` (Xanh dương - Tương tự Telegram, tạo cảm giác app liên lạc/quản lý)
- **Primary Variant:** `#1A7AAB` (Xanh dương đậm - Dùng cho trạng thái pressed/hover của nút)
- **Secondary:** `#FFD952` (Vàng Gold - Dùng cho cảnh báo, thành tựu, điểm nhấn nhỏ)

### Nhóm Semantic (Nền & Bề mặt)
Dùng cho nền App, nền Card, Dialog. Phân cấp độ sâu bằng độ sáng của màu Xám Xanh (Slate).
- **Background (Nền app chính):** `#0F172A` (Slate 900)
- **Surface (Nền Card/Hộp thoại):** `#1E293B` (Slate 800)
- **Surface Variant (Nền phụ/Input/Khối phụ):** `#334155` (Slate 700)
- **Text Primary (Chữ chính):** `#F8FAFC` (Trắng hơi xanh)
- **Text Secondary (Chữ phụ/Mô tả):** `#94A3B8` (Xám nhạt)
- **On Primary (Chữ trên nút Primary):** `#FFFFFF` (Trắng tinh)

---

## 3. Hệ Thống Typography (Phông Chữ)
Áp dụng chiến lược **Single Font Family** để tối ưu hóa hiệu năng render text.

- **Global Font Family:** `Roboto, sans-serif` (Chỉ dùng font mặc định của Android).
- **Tuyệt đối KHÔNG** load hay include bất kỳ file `.ttf`, `.otf` ngoại lai nào (như Orbitron, Outfit).

**Quy chuẩn Mapping:**
- `fragment_title` (Tiêu đề màn hình): Roboto, Trọng lượng: **900** (Black)
- `character_heading` (Tên thẻ bài/Nhân vật): Roboto, Trọng lượng: **700** (Bold)
- `resource_value_technical` (Số lượng xu, thẻ, kim cương): Roboto, Trọng lượng: **700** (Bold)
- `buttons_text` (Chữ trong nút bấm): Roboto, Trọng lượng: **700** (Bold)
- `body_and_dialog_text` (Nội dung hộp thoại, đoạn văn): Roboto, Trọng lượng: **400** (Regular)

---

## 4. Hệ Thống Hình Khối (Shapes) & Bo Góc
Phân tách rõ ràng giữa "Giao diện điều khiển" (UI) và "Vật phẩm đồ họa" (Objet).

### Đối với UI Elements (Nút bấm, Chip, Thanh trạng thái, Hộp thoại)
- Sử dụng độ bo góc (Corner Radius) khổng lồ: `33px` (Hình viên thuốc - Pill shape).
- **Mục đích:** Tương phản hoàn toàn với các thẻ bài vuông vắn, giúp người dùng dễ nhận biết đâu là nút bấm để thao tác, đâu là vật phẩm để ngắm nhìn.

### Đối với Objets (Thẻ bài, Hình nhân vật)
- **Quy tắc:** Giữ nguyên độ bo mặc định (thường là `12dp` hoặc `16dp` do engine định nghĩa).
- **Lý do kỹ thuật:** Việc bo tròn quá mức (`33px`) lên các hình ảnh có độ phân giải cao sẽ cắt xén mất artwork của nhân vật. Quan trọng hơn, việc thiết bị Android 9 phải tính toán clipping mask với bo góc lớn cho 20.000 thẻ bài sẽ gây thắt cổ chai CPU (Performance Bottleneck).

---

## 5. Đặc tả Kỹ thuật: Liquid Glass trên Android 9
Để đáp ứng yêu cầu UI kính mờ nhưng không gây lag trên giả lập Android 9:
1. **NO Real-time Blur:** Nghiêm cấm sử dụng `RenderScript`, `RenderEffect.createBlurEffect()`, hay thư viện làm mờ `BlurView` thời gian thực lên các Card đang cuộn.
2. **Static Liquid Glass:**
   - Dùng màu `Surface` (`#1E293B`) kết hợp kênh Alpha (Trong suốt khoảng 40% - 60%).
   - Phủ lên trên một file `drawable` lặp (Tile Mode) chứa **hạt nhiễu tĩnh (Static Noise PNG)** có kích thước siêu nhỏ (ví dụ: 16x16 pixel lặp lại).
   - Thêm viền `stroke` mỏng 1dp, màu `#F8FAFC` alpha 20% dạng đứt nét (`dashWidth`, `dashGap`) để tạo cảm giác kính nổi (Elevated).
   - **Hiệu năng:** Phương pháp này chỉ cần GPU vẽ 2 lớp đồ họa tĩnh cực nhẹ, đạt FPS tối đa trên mọi máy giả lập cũ.
