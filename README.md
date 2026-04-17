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
| **Android Client** | Java (Android Studio), Retrofit 2, OkHttp (32-Threads), Glide, Google ML Kit (AI Auto Face-Crop Avatar), Lottie Animation. |
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

## 📜 Nhật Ký Cập Nhật (V5.0 - Formation Passive Synergy)
*   ✅ **Kiến trúc Formation Mới:** Hệ thống Đội hình (Team Formation) chuyển đổi hoàn toàn sang Passive Synergy 100%. 6 slot trên sân tự động cộng dồn vô hạn Buff mà không cần người chơi phải kích hoạt thẻ thủ công.
*   ✅ **Staggered Grid Layout:** Sân khấu 6 thẻ được bố trí theo dạng dích dắc (1-2-1-2) mô phỏng chính xác giao diện chuẩn. Hỗ trợ đầy đủ Drag & Drop hoán đổi vị trí thẻ mượt mà thông qua `ItemTouchHelper`.
*   ✅ **Realtime OVR Animator:** Tự động Count-Up/Count-Down điểm số OVR khi thay đổi đội hình.
*   ✅ **Interactive Synergy Dashboard:** Bảng báo cáo Buff động ở Footer. Giữ đè (Long Press) vào bất kỳ tên Buff nào để tự động:
    * Làm tối (Dim) các thẻ không liên quan.
    * Làm sáng viền (Glow) các thẻ đang mang Buff.
    * Kích hoạt mạng lưới năng lượng (Neon Lines) nối các thẻ với nhau nhờ Canvas Custom View (`NeonLineView`).

*   **Tài liệu Hệ thống API Mới:**
    *   **Battle Preview Endpoint:**
        *   **Method / URL:** `POST /api/battle/preview`
        *   **Request:** `BattleRequest` (JSON gồm mảng `formation` chứa `userCardId` của 6 slot)
        *   **Response:** `BattleResponse` trả về `totalOvr` và danh sách `activeSynergies` để UI tự động render mà không cần tính toán nội bộ.
    *   **Gacha Upgrade Endpoint:**
        *   **Method / URL:** `POST /api/gacha/upgrade`
        *   **Request:** `UpgradeRequest` (JSON gồm `userId`, `baseCardId`, `materialCardIds` mảng các materials)
        *   **Response:** `UpgradeResponse` chứa thông báo thành công `success`, tin nhắn trả về `message` và cấp độ mới `newLevel`.
113:     *   **Collection Book Endpoint:**
114:         *   **Method / URL:** `GET /api/collection/book/{userId}`
115:         *   **Response:** `CollectionBookResponse` chứa `totalCards`, `ownedCount` và danh sách `entries` (metadata + trạng thái sở hữu `owned`).

---
## 📜 Nhật Ký Cập Nhật (V5.0 - Professional Auth & UX Standardization)
*   ✅ **Galactic UI 2026 (Auth Flow):** Chuẩn hóa toàn bộ màn hình `SignIn`, `SignUp` và `ForgotPassword` theo phong cách vũ trụ cao cấp (Galactic Dark Mode), kết hợp hiệu ứng hình nền Parallax trôi nổi, Glassmorphism mờ ảo và các nút bấm hiệu ứng Rainbow Gradient.
*   ✅ **Advanced UX (Click Debounce):** Triển khai lớp tiện ích `ClickDebounce` giúp ngăn chặn 100% tình trạng spam click/double-click tại các nút bấm quan trọng (Sign In, Send Code, Sign Up, Reset Password), đảm bảo an toàn tuyệt đối cho các yêu cầu gửi về Server.
*   ✅ **Tối ưu luồng điều hướng (User Flow):** Fix lỗi điều hướng mặc định của `MainActivity`, đảm bảo người dùng được đưa vào tab **Home** sau khi Đăng nhập/Đăng ký thay vì Profile.
*   ✅ **Hiệu ứng nhịp đập vũ trụ (Cosmic Heartbeat):** Đồng bộ hóa thời gian chạy (PlayTime) của các hiệu ứng Animation giữa các Activity, tạo cảm giác chuyển cảnh mượt mà không bị ngắt quãng.

---
## 📜 Nhật Ký Cập Nhật (V6.0 - Smart Global Synced Avatar)
*   ✅ **Global Sync Avatar:** Hủy bỏ avatar Local Storage rác, thay thế bằng cơ chế định danh `avatarId` gửi về Backend, giúp tốc độ cập nhật siêu nhanh và nhẹ tải máy. Server và 100% Client đều nhìn thấy cùng 1 hình thông qua API profile.
*   ✅ **Google ML Kit Integration:** Tích hợp bộ quét Deep Learning On-Device cực nhẹ của Google để dò tìm gương mặt Idol trong bức thẻ. Tự động đưa ra `SmartFaceCropTransformation` căn chỉnh Crop Avatar Face Focus tuyệt đối. Không tốn % API cloud nào.
*   ✅ **Inventory-Based Selector:** Bộ chọn Avatar BottomSheet siêu đẹp ứng dụng quy chuẩn Galactic Glassmorphism. Chỉ hiển thị các Objet mà User ĐÃ SỞ HỮU thực tế trong rương đồ, tích hợp auto-fallback tự xài mặc định nếu cần.

---
## 🎯 Nhật Ký Cập Nhật (V6.1 - Unified Inventory System)
*   ✅ **Gộp chung BottomSheet:** Thay thế hoàn toàn `SelectObjetFragment`, `UpgradeBottomSheet` bằng duy nhất một `InventoryBottomSheet` nhằm tối ưu bộ nhớ.
*   ✅ **Hỗ trợ Đa chức năng:** `InventoryBottomSheet` hiện giờ hỗ trợ cả Single-Select (dùng cho Spin, Home Showcase) và Multi-Select (xem trước tỷ lệ % thành công cho Upgrade, tối đa 5 materials).
*   ✅ **Cải thiện UI:** Fix vấn đề dropdown dính lề màn hình do sử dụng `MarginLayoutParams` thay vì LayoutParams hệ thống cũ trong môi trường ConstraintLayout.
*   ✅ **Clean Code (Don't Repeat Yourself):** Loại bỏ logic rườm rà lặp lại ở CollectionFragment và UpgradeFragment, cung cấp giao tiếp trực tiếp qua Listener mà không cần Fragment Result truyền thống.

## 🎯 Nhật Ký Cập Nhật (V6.2 - Strict Formation Layout & Validator & Cloud Sync)
*   🛡️ **Triển khai Auto-Scale Layout (1-2-1-2):** Đã xóa `RecyclerView` (với cấu trúc Span) trong màn hình `FormationActivity`, được thay thế hoàn toàn bởi cấu trúc ConstraintLayout lồng ghép (`LinearLayout` chia weight tuyệt đối). Đảm bảo thẻ ở trên sân *tự động co giãn theo tỷ lệ 1:1.54* để full-view và không bao giờ bị méo hình. (Kèm theo chức năng Native Drag & Drop mượt mà hơn).
*   ✅ **Chống Trùng Lặp Nghệ Sĩ (Artist Duplicate Lock):** Cải tiến thuật toán ở `InventoryBottomSheet`, chặn hoàn toàn hành vi của người chơi khi đưa 2 thẻ có chung Artist (`member`) vào cùng 1 đội hình. Hiển thị cảnh báo trực quan bằng `Toast` thay vì cho phép tráo đổi vô nghĩa.
*   ☁️ **Đồng bộ Đội Hình (Cloud Formation Sync):** Mỗi khi người chơi điều chỉnh thẻ bài (Kéo thả, đổi, đặt vào sàn), đội hình sẽ được âm thầm tự động lưu ngầm (`id` thẻ phân tách bằng dấu `,`) vào thẳng Field `activeFormation` của bảng `User` tại Database. Khi Game mở lại, cấu hình cuối sẽ được tự gọi về!

## 🎯 Nhật Ký Cập Nhật (V7.0 - Premium Gift System Redesign)
*   🎁 **Kiến trúc Quà Tặng Độc Lập:** Tách biệt module quà tặng thành một Wizard 3-Step chuẩn quốc tế. Cấm tặng thẻ đang trong Formation và có phí chống lạm phát (36k Coin + 36 Diamond).
*   UI/UX UI chuẩn: Đồng nhất ngôn ngữ (Full Tiếng Anh) và áp dụng Layout Constraint (1:1.54) cho view thẻ 3-D. Bổ sung `Search` cho việc chọn bạn và nút `Previous` ở các Step 2, Step 3 để hỗ trợ hoàn tác.
*   Hiệu ứng 3D Premium: Objet khi được đem đi tặng sẽ bay lơ lửng ở chính giữa và liên tục lật xoay quanh trục Y (Animation ObjectAnimator) tạo điểm nhấn mạnh mẽ.

---
## 🏆 Nhật Ký Cập Nhật (V7.1 - Collection Book & Album Feature)
*   🏆 **Album Feature (Pokédex-style):** Triển khai tab "Album" mới trong `CollectionFragment`, hiển thị 100% danh sách thẻ có trong `database.json` thay vì chỉ hiển thị thẻ đang sở hữu.
*   🌑 **Phân loại Visual (Silhouette):** Thẻ chưa sở hữu hiển thị dưới dạng bóng đen (Grayscale + Silhouette) kèm icon khóa. Thẻ đã sở hữu hiển thị rõ nét kèm huy hiệu OVR và viền tím Metallic.
*   📊 **Collection Progress:** Tích hợp thanh Progress bar Galactic cập nhật thời gian thực tiến độ thu thập (Ví dụ: "45/350 - 12.8%").
*   ⚙️ **Backend Cross-Reference:** Xây dựng `CollectionBookService` tối ưu, tự động so khớp toàn bộ Metadata hệ thống với kho đồ cá nhân của User để trả về trạng thái Collection chính xác nhất.
*   🔄 **Fix Navigation Shift:** Tự động điều chỉnh Index các tab sau khi chèn thêm Album vào vị trí đầu tiên, đảm bảo các link điều hướng từ Shop và Home vẫn hoạt động chính xác.

---
## ✨ Nhật Ký Cập Nhật (V7.2 - Premium Collection Modernization)
*   🎁 **Hệ thống Milestone Rewards:** Giới thiệu cơ chế thu thập quà tặng tại các mốc tiến trình (30%, 60%, 100%). Hiệu ứng Pulse nhịp đập mời gọi và hệ thống lưu trạng thái quà (Shared Preferences) chống spam.
*   🌟 **Visual Effect Sync (1:1):** Nâng cấp thẻ trong Album ngang tầm với Objets. Tích hợp tự động Color-Match Glow (hào quang theo màu thẻ), Shiny Shimmer (vệt sáng bóng) và Floating ObjectAnimator. Tất cả được tái chế an toàn qua ViewHolder recycle!
*   🎆 **Premium Reward Reveal:** Hiệu ứng đập hộp (Lottie + Scale-up Animator) khi nhận quà. Xuất hiện trên nền Dialog Galactic Gradient mới `bg_dialog_galactic.xml` mang lại trải nghiệm mãn nhãn.
*   🛡️ **Advanced Collection Multi-Filter:** Bộ lọc lưới AND nâng cao. Tự động chuyển đổi Category mapping giữa Raw DB Class và Display UI. Sắp xếp tùy chỉnh OVR, Cấp độ và Ngày tháng với logic thông minh.

---
## 📦 Nhật Ký Cập Nhật (V7.0 - Bulk Pack Opening System)
*   ✅ **High-Performance Bulk Opening:** Nâng cấp `PackService` để hỗ trợ mở đồng thời lên đến 36 packs chỉ trong 1 Request. Hệ thống tự động xử lý khấu trừ túi đồ và sinh thẻ hàng loạt trong một Transaction duy nhất.
*   ✅ **Dynamic Response Payload:** Cấu trúc dữ liệu API mới trả về danh sách toàn bộ thẻ đã mở, giúp Client hiển thị kết quả tổng hợp một cách nhanh chóng.
*   ✅ **Non-Blocking UI (ItemRevealFragment):** Tối ưu hóa giao diện hiển thị kết quả. Khi mở hàng loạt (>1 pack), hệ thống tự động bỏ qua các cutscene dài và hiển thị lưới kết quả premium, giúp người chơi tiết kiệm thời gian mà vẫn giữ được cảm giác "Galactic".
*   ✅ **API Endpoint Mới:**
    *   **Method / URL:** `POST /api/pack/open`
    *   **Params:** `userId` (Long), `packCode` (String), `quantity` (int - mặc định là 1).
    *   **Response:** Trả về JSON chứa danh sách `cards` (cardId và cardData) kèm trạng thái thành công.

---
## 🎬 Nhật Ký Cập Nhật (V7.2 - Dynamic Cinematic Overlay & Fullscreen Result)
*   ✅ **Dynamic Overlay Architecture (V2):** Tái cấu trúc toàn bộ hệ thống Cutscene sang cơ chế tạo View động (Dynamic View Creation). Xóa bỏ sự phụ thuộc vào XML tĩnh để triệt tiêu 100% lỗi treo màn hình do thiếu linh kiện.
*   ✅ **Interactive VIP Text Sequence:** Nâng cấp Tier 3 Cutscene giúp tự động đồng bộ và hiển thị thông tin Artist (Class, Season, S-ID) theo nhịp video, mang lại cảm giác sống động và chuyên nghiệp.
*   ✅ **Fullscreen Premium Result:** Kết quả mở thẻ được hiển thị trên lớp phủ Glossy Black (`#0e0e0e`) với hiệu ứng **Overshoot & Float Animation**, tạo sự khác biệt đẳng cấp giữa thẻ Common và thẻ VIP.
*   ✅ **Cleanup Auto-Logic:** Tự động dọn dẹp tài nguyên và Overlay ngay khi người dùng nhấn "AWESOME", đảm bảo bộ nhớ luôn sạch sẽ và sẵn sàng cho lần mở tiếp theo.

---
## 🛠 Nhật Ký Cập Nhật (V7.3 - Stability & Collection Sync Fix)
*   ✅ **Fix Compilation Error:** Khắc phục lỗi "cannot find symbol cardId" tại `PackService` do biến bị giới hạn phạm vi trong vòng lặp roll thẻ.
*   ✅ **Bulk Sync Collection:** Đảm bảo khi mở nhiều pack cùng lúc (Bulk Open), TẤT CẢ các thẻ mới nhận được đều được tự động cập nhật vào danh sách "Đã sở hữu" (Ever Owned) để đồng bộ chính xác với Album/Collection Book.
*   ✅ **Optimized Database Writes:** Gom nhóm yêu cầu save User sau khi hoàn tất vòng lặp xử lý thẻ, giảm thiểu số lượng Transaction không cần thiết.

---

---
*© 2026 Mosco Project - Advanced Agentic Coding Team.*
