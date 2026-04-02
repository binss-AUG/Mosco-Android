# Phân Tích & Lộ Trình Nâng Cấp Dự Án Mosco (Hướng Đến 2026)

## 1. Phân Tích Chi Tiết Hiện Trạng
### Kiến trúc hiện tại
Dự án Mosco đang sử dụng mô hình Client-Server truyền thống:
- **Client (Mobile App):** Là một ứng dụng Android Native. Logic Gacha một phần được xử lý offline qua class `SpinSystem` hoặc gọi qua API.
- **Server (Backend):** Sử dụng Spring Boot cung cấp các RESTful APIs phục vụ quản lý user, kho đồ, và xử lý logic quay thẻ online.

### Công nghệ đang sử dụng
- **Client:** Viết bằng Java 11. Các thư viện chính: AndroidX, Material Components, Retrofit, Glide, Room Database, Firebase (Auth/Firestore).
- **Server:** Java 21, Spring Boot 3.4.2, Spring Data JPA, H2 Database (File-based), Gson, OkHttp3.

### Hiệu suất
- **Client:** Sử dụng video raw (`.mp4`) để làm hiệu ứng quay thẻ. Điều này làm tăng kích thước file APK đáng kể và tiêu tốn nhiều RAM, CPU để giải mã video khi chạy, có thể gây lag trên thiết bị yếu.
- **Server:** H2 Database hoạt động tốt ở môi trường dev, nhưng khi triển khai production có thể gây nghẽn cổ chai (bottleneck) vì tính chất file-lock khi có lượng lớn người dùng tương tác đồng thời.

### Trải nghiệm người dùng (UX)
- Giao diện được thiết kế theo `design_system.md` ("Galactic Interface"), đồng bộ, đẹp mắt với các hiệu ứng ghost border, màu sắc neon. Tuy nhiên, luồng mở thẻ (gacha) đang phụ thuộc vào video cứng nên thiếu tính linh hoạt và tương tác thời gian thực.

### Bảo mật
- **Server:** Sử dụng Spring Security cơ bản, xác thực bằng JWT, nhưng mật khẩu có thể chưa được cấu hình các rule khắt khe (Zero-trust) và chưa có cơ chế chống Spam/Rate Limiting API bảo vệ hệ thống trước DDoS.
- Tồn tại logic tính toán "SpinSystem" trên Client, tạo lỗ hổng rủi ro cho phép hacker decompile và cheat kết quả Gacha offline.

---

## 2. Đánh Giá Điểm Mạnh & Điểm Yếu

### Điểm mạnh
- **Tính ổn định của Backend:** Sử dụng Java 21 và Spring Boot 3.4.2 là các công nghệ khá mới và cực kỳ ổn định cho Backend.
- **Tính năng cốt lõi rõ ràng:** Logic Gacha được thiết kế chi tiết, bao gồm cả "Quantum Swap Matrix" cho UI và "True Random" gọi API từ tiếng ồn khí quyển trên Server.
- **UX/UI chuyên nghiệp:** Đã có tài liệu Design System chuẩn chỉnh, quy định rõ màu sắc, component và philosophy.
- **Khả năng Offline:** Hỗ trợ Room DB để lưu cache thông tin, giúp ứng dụng không bị phụ thuộc 100% vào mạng.

### Điểm yếu
- **Công nghệ Client lỗi thời:** Viết ứng dụng Android bằng Java 11 là quá cũ so với tiêu chuẩn hiện tại (Kotlin, Jetpack Compose).
- **Trùng lặp Logic:** Logic `SpinSystem` tồn tại ở cả Client và Server gây khó khăn khi maintain (phải update JSON 2 nơi).
- **Hạn chế quy mô (Scale):** CSDL H2 không thể dùng cho Production.
- **Tối ưu hóa tài nguyên (Kém):** Video tĩnh `.mp4` trong app làm APK nặng và tốn pin (không phù hợp với xu hướng Green Coding).
- **Thiếu tính đa nền tảng & AI:** Chỉ chạy trên Android, chưa có AI gợi ý, chưa hỗ trợ WebAssembly hay PWA.

---

## 3. Danh Sách Các Task Ưu Tiên Cao (Cần Fix/Thay Thế)

| Task | Mô tả công việc | Công nghệ đề xuất | Thời gian | Lợi ích dự kiến |
|---|---|---|---|---|
| **1. Migrate Android Java sang Kotlin & Jetpack Compose** | Viết lại toàn bộ UI từ XML và Java sang Kotlin/Compose. | Kotlin, Jetpack Compose (Open-source) | 4 tuần | Code ngắn gọn hơn 50%, dễ bảo trì, hiệu năng UI tăng cao, sẵn sàng cho KMP (Kotlin Multiplatform). |
| **2. Chuyển đổi Database Server** | Thay thế H2 Database bằng PostgreSQL để xử lý đồng thời tốt hơn. | PostgreSQL, Flyway (Migration) | 1 tuần | Đảm bảo tính toàn vẹn dữ liệu, khả năng scale tốt cho hàng triệu user. |
| **3. Thay thế Video Gacha bằng Animation Động** | Xóa các file `.mp4` nặng. Dùng animation vector thời gian thực. | Lottie, Rive, hoặc Spine (Open-source/Free tier) | 2 tuần | Giảm APK size từ >50MB xuống <15MB, mượt mà trên thiết bị yếu, giảm tiêu thụ pin (Green Coding). |
| **4. Centralize Logic Gacha lên Server (Serverless)** | Loại bỏ `SpinSystem` ở Client. Chuyển toàn bộ việc tính toán kết quả Gacha thành Serverless Function để giảm tải cho Server chính. | OpenFaaS / AWS Lambda (Local: Knative) | 2 tuần | Chống cheat/hack 100%, dễ dàng update tỉ lệ drop rate mà không cần user cập nhật app. |
| **5. Áp dụng Rate Limiting & Zero-Trust** | Thêm lớp API Gateway để giới hạn request, xác thực 2 lớp, chống DDoS. | Redis, Spring Cloud Gateway | 2 tuần | Tăng cường bảo mật cấp doanh nghiệp, bảo vệ tài nguyên máy chủ. |

---

## 4. Lộ Trình Thực Hiện Nâng Cấp (Đạt Chuẩn 2026)

### Giai đoạn Q1: Nền tảng cốt lõi & Green Coding
- **Tháng 1-2:** Thực hiện chuyển đổi từ Java sang Kotlin. Áp dụng Jetpack Compose cho toàn bộ giao diện hiện tại.
- **Tháng 3:** Migrate database H2 sang PostgreSQL. Loại bỏ video raw `.mp4` và thay thế bằng Lottie/Rive (Green Coding - giảm mức tiêu thụ điện năng của thiết bị di động). Xóa bỏ logic `SpinSystem` ở client.

### Giai đoạn Q2: Bảo mật Zero-Trust & Kiến trúc Microservices/Serverless
- **Tháng 4-5:** Chia nhỏ backend Spring Boot. Đưa logic Spin nặng nề sang mô hình Serverless (chạy khi có request).
- **Tháng 6:** Tích hợp Spring Cloud Gateway và Redis để làm Rate Limiting. Triển khai kiến trúc Zero-trust: yêu cầu xác minh token chặt chẽ ở mọi node, mã hóa data at rest.

### Giai đoạn Q3: Tích hợp Trí tuệ Nhân tạo (AI)
- **Tháng 7-8:** Tích hợp AI Recommendation (Gợi ý gói pack dựa trên sở thích người chơi) bằng các model mã nguồn mở (ví dụ: TensorFlow Lite chạy on-device hoặc API từ HuggingFace).
- **Tháng 9:** Tích hợp Generative AI (Stable Diffusion / LLM) để tự động sinh ra mô tả hoặc hình ảnh cho các thẻ bài "Unique" (độc bản) trực tiếp cho từng user.

### Giai đoạn Q4: Đa nền tảng (Wasm, PWA) & Hoàn thiện
- **Tháng 10-11:** Sử dụng Kotlin Multiplatform (KMP) để compile chung logic mạng và database. Biên dịch giao diện Compose sang WebAssembly (Wasm) để tạo phiên bản Web siêu nhẹ, hoạt động như một PWA (Progressive Web App).
- **Tháng 12:** Tối ưu hóa, kiểm thử bảo mật diện rộng (Pen-test), ra mắt chính thức phiên bản 2026.

---

## 5. Tiêu Chí Thành Công (Success Criteria)
1. **Hiệu suất & Dung lượng:**
   - Kích thước ứng dụng (APK/AAB) giảm ít nhất 50% (loại bỏ video tĩnh).
   - Mức tiêu thụ pin giảm 30% khi quay gacha (đáp ứng tiêu chí Green Coding).
   - Tốc độ phản hồi API trung bình dưới 100ms.
2. **Kỹ thuật & Codebase:**
   - 100% mã nguồn Client là Kotlin.
   - Code coverage (Test) đạt > 80% trên cả Client và Server.
3. **Bảo mật & Scalability:**
   - Server chịu tải được > 10,000 CCU (Concurrent Users) mà không bị nghẽn (nhờ PostgreSQL và Redis Cache).
   - Hệ thống chặn 100% các hành vi spam/gacha trái phép thông qua Zero-Trust & Rate Limiting.
4. **Mở rộng (Platform):**
   - Ứng dụng chạy mượt mà trên cả Android và Trình duyệt Web (thông qua WebAssembly / PWA) với cùng một source code lõi.