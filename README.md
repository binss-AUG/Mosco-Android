# 🪐 Mosco - The Galactic Gacha Project

Chào mừng bạn đến với dự án **Mosco**. Đây là một ứng dụng di động mô phỏng game thẻ bài (Gacha/Spin) cao cấp, được thiết kế với triết lý **"Local-First Architecture"** để mang lại trải nghiệm mượt mà tuyệt đối (Zero-Lag) và giao diện siêu thực (Galactic UI).

---

## 🚀 Điểm Nhấn Dự Án (Project Highlights)

### 1. Kiến trúc Tài nguyên "Local-First"
Để xử lý hơn **10,000+ tài nguyên thẻ bài** mà không gây lag mạng:
*   **Siêu tốc độ (OkHttp 32 Threads):** Hệ thống sử dụng Thread Pool 32 luồng đồng thời kết hợp với OkHttp để tải toàn bộ tài nguyên từ Cloudflare ngay khi khởi động.
*   **Chiến thuật "Local Thumbnail First":** Ưu tiên nạp ngay bản 2x từ bộ nhớ máy (0ms) làm ảnh chờ, sau đó mới nạp bản 4x chất lượng cao từ server và lồng vào nhau bằng hiệu ứng Cross-Fade 500ms.
*   **Visual Deception Loading:** Splash Screen sử dụng thuật toán tiến trình phi tuyến tính (Fake Progress) giúp đánh lừa thị giác người dùng, tạo cảm giác app khởi động cực nhanh ngay cả khi đang xử lý dữ liệu nặng.

### 2. Trải nghiệm Gacha Cao Cấp (Premium Spin System)
*   **Hệ thống Spin 5 giai đoạn:** Bao gồm chọn thẻ hi sinh -> Video cutscene -> Ma trận 16 lá bài bí ẩn -> Hiệu ứng lật thẻ 3D -> Kết quả kịch tính kèm hiệu ứng Neon.
*   **Quantum Swap Matrix:** Logic hoán đổi thực tại giúp đồng bộ kết quả từ Server với vị trí người dùng chọn trên lưới mà không làm lộ dữ liệu trước.
*   **Aesthetics First:** Sử dụng Poppins fonts, Lottie animations (`loading.json`) và bảng màu Gradient theo phong cách Galactic Dark Mode.

### 3. Hiệu năng Đỉnh cao (Extreme Optimization)
*   **Bye-bye Jank:** Loại bỏ hoàn toàn độ trễ khi nạp tài nguyên tĩnh (Card Back, Trash Objet) bằng cách nạp trực tiếp Resource thay vì qua Glide.
*   **Memory Caching:** Sử dụng cấu trúc lưu trữ tập trung giúp truy xuất thông tin thẻ bài tức thì tại mọi màn hình (Inventory, Shop, Detail).

---

## 🛠 Tech Stack

| Thành Phần | Công Nghệ Sử Dụng |
| :--- | :--- |
| **Android Client** | Java (Android Studio), Retrofit 2, OkHttp (32-Threads), Glide, Lottie Animation. |
| **Backend Server** | Java 21, Spring Boot 3.4.2, Spring Data JPA, JWT Security, MySQL 8.x. |
| **Asset Delivery** | Cloudflare Images (1x, 2x, 4x, Original), Internal Storage Caching. |
| **Design System** | Galactic Dark Mode, Poppins Typography, Glassmorphism. |

---

## 📁 Cấu Trúc Hệ Thống

```text
Mosco/
├── client/                     # 📱 Android Native Client
│   ├── app/src/main/
│   │   ├── java/.../fragment/  # SpinFragment, ItemRevealFragment, SelectObjetFragment
│   │   ├── java/.../utils/     # CardAssetManager (32-thread), SessionManager
│   │   └── res/raw/            # loading.json (Lottie), spin_animation.mp4
│   └── design_system.md        # Quy chuẩn thiết kế Galactic
│
└── server/                     # 🖥️ Spring Boot Backend
    ├── src/main/java/.../controller/ # GachaController, InventoryController
    ├── src/main/java/.../service/    # SpinSystem logic, GachaService
    └── resources/application.properties # Cấu hình MySQL & JWT
```

---

## 📑 Quy Tắc Phát Triển (Coding Standards)

Dự án Mosco tuân thủ bộ quy tắc **"3 Nhất"**:
1.  **Ngắn nhất:** Giải pháp đi thẳng vào vấn đề, sử dụng thư viện chuẩn.
2.  **An toàn nhất:** Xử lý ngoại lệ chặt chẽ, kiểm tra Null 100% tại Client để tránh crash.
3.  **Dễ Scale nhất:** Tách biệt hoàn toàn logic gọi dữ liệu (Repository) và hiển thị UI (Fragment/Activity).

> [!IMPORTANT]
> **Ngôn ngữ:** Chỉ sử dụng duy nhất **Java**. Tuyệt đối không dùng Kotlin.
> **Comment:** 100% bằng tiếng Việt giải thích logic "tại sao".

---

## 🚦 Hướng Dẫn Setup Nhanh

### 1. Backend
1. Cấu hình thông số MySQL trong `server/.env`.
2. Chạy `.\gradlew.bat bootRun` để khởi động Server (Port 8080).
3. Sử dụng `schema-mysql.sql` để tạo cấu trúc DB.

### 2. Android Client
1. Cập nhật `base_url` trong `strings.xml` trỏ về IP của Server.
2. Đảm bảo file `loading.json` có mặt trong `res/raw`.
3. Build & Run bằng Android Studio.

---

## 📜 Nhật Ký Cập Nhật (V3.0 - Local First Update)
*   ✅ Nâng cấp `CardAssetManager` lên 32 luồng tải song song với OkHttp Dispatcher.
*   ✅ Triển khai chiến thuật "Local Thumbnail First" cho 100% màn hình hiển thị thẻ.
*   ✅ Hoàn thiện Splash Screen với Lottie và thuật toán Fake Progress 2.0.
*   ✅ Tối ưu hóa SpinFragment: Loại bỏ Jank khi nạp tài nguyên tĩnh, thêm các khoảng trễ "vàng" (0.1s - 1s) để tạo nhịp điệu kịch tính.

## 📜 Nhật Ký Cập Nhật (V4.0 - Server-Side Upgrade Integration)
*   ✅ **Kiến trúc Server-Side Update:** Di chuyển toàn bộ tính năng gacha upgrade từ Client về Server, kết nối qua `UpgradeService` và `UpgradeController` để đảm bảo Single Source of Truth chống hack/cheat.
*   ✅ **Đồng bộ hóa UI vĩnh viễn (OVR/Level Badges):** Tính toán điểm số OVR và hệ thống cache `DatabaseLoader` đã được ánh xạ toàn bộ qua Client (Inventory, Collection, Spin, Upgrade) để đảm bảo thẻ luôn có chỉ số và badge đúng với Database.
*   ✅ **Các dependency mới:** (Không phát sinh).
*   ✅ **Cấu trúc Database Update:** Bổ sung các Custom Query `deleteByUser` và `findByIdAndUserId` vào `UserCardRepository`.

*   **Tài liệu Hệ thống API Mới:**
    *   **Gacha Upgrade Endpoint:**
        *   **Method / URL:** `POST /api/gacha/upgrade`
        *   **Request:** `UpgradeRequest` (JSON gồm `userId`, `baseCardId`, `materialCardIds` mảng các materials)
        *   **Response:** `UpgradeResponse` chứa thông báo thành công `success`, tin nhắn trả về `message` và cấp độ mới `newLevel`.

---
*© 2026 Mosco Project - Advanced Agentic Coding Team.*
