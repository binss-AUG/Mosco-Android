# Kế Hoạch Triển Khai Hệ Thống Huy Hiệu Danh Dự (Honor Badges System) - Bản Hoàn Thiện

Bản kế hoạch này mô tả kiến trúc thiết kế và lộ trình triển khai chi tiết hệ thống Huy hiệu danh dự (Honor Badges) phân cấp 6 bậc: **Iron (Sắt)** -> **Bronze (Đồng)** -> **Silver (Bạc)** -> **Gold (Vàng)** -> **Diamond (Kim Cương)** -> **EX (Master)**.

---

## Nguyên Tắc Thiết Kế Huy Hiệu (Simple & Professional)

Huy hiệu sẽ được đặt tên theo công thức: `{Tier} {BadgeType}` (Ví dụ: `Bronze Spin Master`, `EX Duo Flame`). 
Tên hiển thị tiếng Anh chuyên nghiệp trên giao diện:
- **Spin Master** (Bậc thầy quay thẻ)
- **Pack Master** (Bậc thầy mở gói)
- **Collection Master** (Bậc thầy sưu tập)
- **Immortal** (Kẻ bất tử - Login Streak)
- **Duo Flame** (Cặp đôi bá đạo - Couple Streak)
- **Celebrity** (Người nổi tiếng - Likes Count)
- **Golden Hammer** (Chiếc búa vàng - Card Upgrade Level)

### Giải Pháp Đồ Họa Tối Ưu (Layering & Tinting)
Để đảm bảo tính đa dạng mà không làm tăng kích thước ứng dụng không cần thiết:
1. **7 Tệp XML Vector Icon** đơn sắc biểu trưng cho 7 loại Huy hiệu.
2. **6 Tệp XML Shape Background** biểu trưng cho 6 Cấp bậc (Sắt -> EX).
3. Sử dụng thuộc tính `android:tint` động trên Layout để tự kết hợp màu sắc tương ứng.

---

## Token Màu Sắc Mới Đăng Ký (Trong `colors.xml`)

```xml
<!-- Badge Tier Colors -->
<color name="badge_tier_iron">#64748B</color>         <!-- Sắt: Slate Gray -->
<color name="badge_tier_bronze">#D97706</color>       <!-- Đồng: Bronze Metallic -->
<color name="badge_tier_silver">#94A3B8</color>       <!-- Bạc: Silver Metallic -->
<color name="badge_tier_gold">#F59E0B</color>         <!-- Vàng: Gold Brand -->
<color name="badge_tier_diamond">#06B6D4</color>      <!-- Kim Cương: Cyan Brand -->
<color name="badge_tier_ex_red">#DC2626</color>       <!-- EX: Neon Crimson Red -->
<color name="badge_tier_ex_dark">#0F172A</color>      <!-- EX: Obsidian Black Background -->
```

---

## Phân Phối Chi Tiết Mốc Chỉ Số (Tất Cả Các Cấp)

| Cấp Bậc (Tier) | Spin Master | Pack Master | Collection Master | Immortal (Login) | Duo Flame (Couple) | Celebrity (Likes) | Golden Hammer (Upgrade) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Iron** | >= 1 spin | >= 1 pack | >= 5% | >= 3 ngày | >= 3 ngày | >= 5 likes | Thẻ Normal +5 |
| **Bronze** | >= 36 spins | >= 36 packs | >= 15% | >= 10 ngày | >= 10 ngày | >= 15 likes | Thẻ Normal +8 |
| **Silver** | >= 100 spins | >= 100 packs | >= 35% | >= 30 ngày | >= 30 ngày | >= 50 likes | Thẻ Normal +10 |
| **Gold** | >= 500 spins | >= 500 packs | >= 60% | >= 100 ngày | >= 100 ngày | >= 150 likes | Thẻ Rare +5 |
| **Diamond** | >= 1000 spins | >= 1000 packs | >= 80% | >= 200 ngày | >= 200 ngày | >= 300 likes | Thẻ Rare +8 |
| **EX** | >= 6700 spins | >= 6700 packs | >= 95% | >= 365 ngày | >= 365 ngày | >= 600 likes | Thẻ Rare +10 |

*Chú thích:*
* *Thẻ Normal:* First, Welcome, Zero.
* *Thẻ Rare:* Double, Special, Motion, Unit, Premier.

---

## Thay Đổi Cấu Trúc Các Component

### 1. Phía Server (Spring Boot API)
- **Cơ chế:** Tính toán hoàn toàn động (Transient - Runtime) tại hàm `populateUserStats` trong [UserController.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/controller/UserController.java) khi trả về dữ liệu User Profile.
- **Tối ưu gánh tải:** Không ghi chép/lưu cứng vào Database, giúp Server chịu tải cực tốt cho 200+ user hoạt động liên tục.
- **Truy vấn Cấp cộng (Upgrade Level):** Bổ sung SQL Query tối ưu vào [UserCardRepository.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/server/src/main/java/com/vn/jet/mosco/spinserver/repository/UserCardRepository.java) để tìm giá trị nâng cấp lớn nhất theo Class:
  ```java
  @Query("SELECT MAX(uc.upgradeLevel) FROM UserCard uc JOIN Card c ON uc.collectionId = c.id WHERE uc.user.id = :userId AND c.cardClass.name = :className")
  Integer findMaxUpgradeLevelByUserIdAndClassName(@Param("userId") Long userId, @Param("className") String className);
  ```

### 2. Phía Client (Android)
- **Tạo mới 7 tệp Vector XML:**
  - `ic_badge_spin.xml` (Vòng xoáy spin)
  - `ic_badge_pack.xml` (Gói thẻ cào)
  - `ic_badge_collection.xml` (Lưới thẻ bài)
  - `ic_badge_immortal.xml` (Ngọn lửa đơn)
  - `ic_badge_duo.xml` (Lửa đôi lồng)
  - `ic_badge_celebrity.xml` (Sao lồng tim)
  - `ic_badge_hammer.xml` (Chiếc búa rèn)
- **Tạo các background cho Cấp bậc:**
  - Nền chip cho EX: `bg_badge_tier_ex.xml` (Đen nhám `#0F172A`, viền Gradient Đỏ `#DC2626`).
- **Adapter Ghép Động:** Cập nhật `BadgeAdapter` trong [ProfileTrophyFragment.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/fragment/ProfileTrophyFragment.java) tự tách chuỗi và load Vector tương ứng + áp dụng Tint màu theo Tier.
- **Delta-Detection:** Triển khai cơ chế phát hiện chênh lệch mảng `badges` trong callback đồng bộ của [ProfileViewModel.java](file:///d:/MEox/UITer/DOAN/Mosco_Megre/Mosco/client/app/src/main/java/com/vn/jet/mosco/ProfileViewModel.java). Kích hoạt thông báo tức thời qua `MoscoNotification.showSuccess`.

---

## Kế Hoạch Xác Minh (Verification)
- **Backend Test:** Viết unit test xác thực hàm ánh xạ trả về đầy đủ và chính xác danh sách badges theo dữ liệu mock.
- **UI Test:** Kiểm tra giao diện hiển thị badge chip trên tab Trophy đảm bảo phối màu đúng chuẩn tối giản, mốc EX hiển thị màu đỏ đen đặc trưng.
