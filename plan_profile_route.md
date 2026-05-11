# 🚀 KẾ HOẠCH TRIỂN KHAI & ĐẶC TẢ KỸ THUẬT: MÀN HÌNH PROFILE ĐỊNH TUYẾN (V2)
**Dự án:** Mosco - Galactic Gacha Game
**Mô đun:** User Profile & Navigation System
**Ngôn ngữ lập trình:** Thuần Java (Nghiêm cấm sử dụng Kotlin)
**Mục tiêu hiệu năng:** Tối ưu RAM, chống OOM trên giả lập Android 9, trải nghiệm "Quiet Luxury".

---

## 1. TỔNG QUAN KIẾN TRÚC (HIGH-LEVEL ARCHITECTURE)
Hệ thống sử dụng kiến trúc **Local-First** với `Room DB` làm "Single Source of Truth". Dữ liệu được ưu tiên đọc từ Local Cache để đảm bảo UI phản hồi tức thì (Zero-latency UI), quá trình đồng bộ API được xử lý ngầm và cập nhật UI thông qua Observer Pattern (`LiveData`).
Cơ chế **Định tuyến Trạng thái (State Routing)** sử dụng một Fragment duy nhất, phân luồng UI dựa trên đối chiếu `TARGET_USER_ID` và `CURRENT_USER_ID`.

---

## 2. CẤU TRÚC DỮ LIỆU & PERSISTENCE (ROOM DB)

### 2.1. Cập nhật Entity `UserStats`
Chốt phương án thiết kế dữ liệu thống nhất giữa Client và Server.
* **Các trường hiện có:** `userId` (PK), `ingameName`, `level`, `exp`.
* **Quyết định về Avatar:** GIỮ NGUYÊN `avatarId`. Loại bỏ ý định dùng `avatarUrl` để tiết kiệm băng thông. Render ảnh bằng `DatabaseLoader.findByCollectionId(avatarId)`.
* **Các trường BỔ SUNG (New):**
    * `currentTitle` (String): Danh hiệu hiển thị (Do Server tính toán và trả về thẳng String).
    * `totalRolls` (int): Tổng số lượt roll (Hiển thị ở khu vực Thống kê).
    * `showcaseCardIds` (List<String>): Danh sách ID thẻ được ghim trưng bày (Max 4 slots).

### 2.2. TypeConverter & Database Migration
* **TypeConverter:** Bắt buộc viết `ShowcaseConverter` để chuyển đổi `List<String>` sang `JSON String` khi lưu vào Room, và ngược lại khi đọc ra.
* **Chiến lược Migration:** Nâng DB Version lên **V3**. Áp dụng **Nuclear Reset** (`fallbackToDestructiveMigration`). 
    * *Action item cho Team:* Yêu cầu toàn bộ Dev/QA "Clear Data" ứng dụng trên thiết bị/giả lập sau khi merge code này.

---

## 3. GIAO DIỆN & TỐI ƯU HIỆU NĂNG (UI/UX)

### 3.1. Phân rã Layout
* **Skeleton Loading (`layout_profile_shimmer.xml`):** Hiển thị Shimmer khi cache trống hoàn toàn.
* **Component Động (ViewStub):** * Sử dụng `ViewStub` (không dùng `<include>` ẩn) cho `layout_owner_actions` (Owner) và `layout_guest_actions` (Guest) để giải phóng RAM. Chỉ gọi `.inflate()` cái nào cần thiết.
* **Asset Khung Viền (Avatar Frame):** Tạm thời dùng `shape_circle_border.xml` (Nét đứt/Gradient tĩnh) làm placeholder. Sẽ thay bằng `LottieAnimationView` khi Design Team cung cấp tài nguyên, không dùng chuỗi ảnh tĩnh gây nặng app.

### 3.2. Khu vực Trưng Bày (Showcase Zone)
* **Giới hạn:** Tối đa **4 thẻ** được ghim (Render dạng Grid 2x2 hoặc Row ngang).
* **Logic:** Đọc `showcaseCardIds` từ DB. Nếu list trống hoặc thiếu slot, hiển thị Placeholder biểu tượng dấu `[+]`.
* *(Ghi chú: Tính năng mở BottomSheet để chọn thẻ ghép vào slot [+] sẽ được chia thành một task riêng ở Phase sau).*

---

## 4. XỬ LÝ NGHIỆP VỤ (BUSINESS LOGIC)

### 4.1. Luồng Dữ liệu Background
* **Đồng bộ DB:** Thay thế hoàn toàn `new Thread()` bằng hệ thống quản lý luồng tập trung `AppExecutors`. Sử dụng `AppExecutors.diskIO()` (SingleThreadExecutor) cho các thao tác `upsert` vào Room để tránh Race Condition.
* **Chống Re-render UI:** Áp dụng `Transformations.distinctUntilChanged()` trong `ProfileViewModel` khi observe LiveData. Chỉ update UI khi dữ liệu thực sự có sự thay đổi.

### 4.2. Xử lý Tương tác (Optimistic UI)
* **Debounce:** Mọi nút bấm tương tác (Follow, Add Friend) phải wrap qua `ClickDebounce` (chuẩn 500ms delay).
* **Cảm giác mượt mà:** Khi bấm Follow, đổi trạng thái nút ngay lập tức (Cập nhật UI thành "Đang theo dõi"), sau đó mới gọi API chạy ngầm. Nếu API lỗi, Toast thông báo và rollback UI.

### 4.3. Bảo mật (Security)
* Check Null Safety tuyệt đối cho `TARGET_USER_ID`. Nếu null, block render và fallback về Current User hoặc đẩy ra trang chủ.
* Masking (Che dấu) các thông tin nhạy cảm nếu View đang ở trạng thái Guest.

---

## 5. ĐIỀU HƯỚNG TRUNG TÂM (NAVIGATION WIRING)

### 5.1. Kiến trúc Single-Activity
* Loại bỏ các Activity rời rạc (như `RankActivity`). Đưa tất cả về dạng Fragment được host trong `MainActivity`.
* Thực hiện chuyển đổi qua `NavigationUtils` sử dụng `FragmentTransaction`.

### 5.2. Quản lý Backstack (Eviction Logic)
Để mô phỏng cảm giác "Deep Dive" mạng xã hội mà không gây OOM:
* **Giới hạn Stack:** Giới hạn tối đa **5 Profile** xếp chồng lên nhau.
* **Cơ chế Eviction:** Khi mở Profile thứ 6, tự động gỡ bỏ (remove/pop) Profile cũ nhất (vị trí số 1) khỏi Fragment Manager.
* **Cơ chế Back:** Khi user nhấn nút Back (Quay lại) liên tục và chạm đến "đáy" của stack, hệ thống tự động đưa user về thẳng trang Sảnh Chính (Home Tab).

---
**[End of Document]**
*Documented by Tech Lead. Required 100% Vietnamese comments in source code implementation.*