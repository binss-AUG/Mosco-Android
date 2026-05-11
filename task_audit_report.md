# 📋 BÁO CÁO ĐỐI CHIẾU ĐẶC TẢ vs HIỆN TRẠNG PROJECT

## Tổng quan

Sau khi rà soát toàn bộ codebase hiện tại, tôi phân loại các tác vụ trong đặc tả thành 3 nhóm:
- ✅ **ĐÃ CÓ** — Đã triển khai, chỉ cần verify/polish
- ⚠️ **CÓ MỘT PHẦN** — Có nền tảng, cần bổ sung
- 🔴 **CHƯA CÓ** — Cần xây mới hoàn toàn

---

## Giai đoạn 1: Data Layer

| Tác vụ | Trạng thái | Chi tiết |
|---|---|---|
| 1.1 UserStats Entity | ⚠️ Có một phần | Entity đã có `@Entity`, `@PrimaryKey(id)`, `ingameName`, `avatarId`, `level`, `exp`. **Thiếu**: `currentTitle`, `totalRolls`, `showcaseCardIds` |
| 1.1 TypeConverter cho List | 🔴 Chưa có | Chưa tồn tại `TypeConverter` nào trong project. Cần tạo mới cho `showcaseCardIds` (List→JSON String) |
| 1.2 UserStatsDao LiveData | ✅ Đã có | `getUserStats(Long userId)` trả về `LiveData<UserStats>` |
| 1.2 Upsert via AppExecutors | ✅ Đã có | `insertUserStats` dùng `@Insert(onConflict = REPLACE)`, `ProfileViewModel` gọi qua `AppExecutors.diskIO()` |

> [!WARNING]
> **Rủi ro Database Migration**: Thêm 3 trường mới (`currentTitle`, `totalRolls`, `showcaseCardIds`) vào `UserStats` sẽ buộc nâng **DB version từ 2 → 3**. Hiện tại đang dùng `fallbackToDestructiveMigration()` + Nuclear Reset, nên cần nhắc user **Clear Data / Uninstall** sau khi cập nhật. Cơ chế Nuclear Reset hiện tại chỉ check `db_ver < 2`, cần cập nhật lên `< 3`.

---

## Giai đoạn 2: Presentation Layer

| Tác vụ | Trạng thái | Chi tiết |
|---|---|---|
| 2.1 fragment_profile.xml chính | ✅ Đã có | Layout phân mảnh đầy đủ: Identity Header, ScrollView, ViewStub cho owner/guest, Shimmer stub |
| 2.1 Shimmer Layout | ✅ Đã có | `layout_profile_shimmer.xml` dùng `ShimmerFrameLayout` với skeleton avatar + username + email + 3 stat blocks |
| 2.1 ViewStub owner/guest | ✅ Đã có | `stub_owner_actions` → `layout_owner_actions.xml`, `stub_guest_actions` → `layout_guest_actions.xml` |
| 2.2 Lottie/WebP Avatar Frame | 🔴 Chưa có | Chưa có hiệu ứng khung viền avatar. Cần asset Lottie hoặc WebP |
| 2.3 Showcase Zone | 🔴 Chưa có | Chưa có Grid/Row trưng bày thẻ, chưa có Placeholder (+), chưa có BottomSheet chọn thẻ |

---

## Giai đoạn 3: Business Logic

| Tác vụ | Trạng thái | Chi tiết |
|---|---|---|
| 3.1 ProfileViewModel | ✅ Đã có | Dùng `Transformations.switchMap`, fetch API → upsert Room qua `AppExecutors` |
| 3.1 distinctUntilChanged | ✅ Đã có | `ProfileFragment` đã gọi `Transformations.distinctUntilChanged(viewModel.getUserStats())` |
| 3.2 Null Safety TARGET_USER_ID | ✅ Đã có | `handleArguments()` check null, fallback về `sessionManager.getUserId()`, Toast cảnh báo |
| 3.2 State Routing (Owner/Guest) | ✅ Đã có | `isOwner` flag, inflate đúng ViewStub, ẩn/hiện btnMenu + btnEditAvatar |
| 3.3 Click Debounce | ✅ Đã có | Guest buttons dùng `ClickDebounce(500, ...)`, Menu dùng manual debounce `MENU_DEBOUNCE_MS` |
| 3.3 Optimistic UI | ⚠️ Có comment TODO | Có comment "Optimistic UI: Đổi nút sang Following" nhưng chưa triển khai thực tế |

---

## Giai đoạn 4: Navigation

| Tác vụ | Trạng thái | Chi tiết |
|---|---|---|
| 4.1 Single-Activity | ✅ Đã có | `NavigationUtils` dùng `FragmentTransaction` trên `MainActivity.frame_layout` |
| 4.1 Eviction Stack (Max 5) | ✅ Đã có | `LinkedList<String> profileStackTags`, check `>= MAX_PROFILE_STACK`, remove oldest |
| 4.1 Self-profile → Tab switch | ✅ Đã có | `openProfile` check `targetUserId == null` hoặc `== currentUserId` → `selectTab(nav_profile)` |
| Back → Home | ✅ Đã có | `ProfileFragment.handleBackAction()` popBackStack hoặc fallback về Home tab |

---

## ❓ CÂU HỎI CHO TECH LEAD

### 1. Về `avatarUrl` vs `avatarId` — Xung đột thiết kế
Đặc tả ghi thêm trường `avatarUrl` vào UserStats. Nhưng hiện tại project đang dùng cơ chế **avatarId-based** (lưu `avatarId` → tra cứu `DatabaseLoader.findByCollectionId()` → lấy `frontImage` URL). Nếu thêm `avatarUrl` thì:
- Server có trả trực tiếp URL avatar không, hay vẫn chỉ trả `avatarId`?
- Có cần duy trì cả 2 cơ chế (avatarId cho Owner local crop, avatarUrl cho Guest từ API)?

### 2. Về `showcaseCardIds` — Ảnh hưởng Server-side
- Danh sách thẻ trưng bày này được lưu ở đâu là nguồn gốc? Server DB hay chỉ Local?
- Nếu là Server, API endpoint nào sẽ trả về / cập nhật `showcaseCardIds`? Hiện tại `getUserStats()` có bao gồm trường này không?
- Giới hạn bao nhiêu thẻ trong Showcase? (Ảnh hưởng đến thiết kế Grid layout)

### 3. Về Lottie Avatar Frame — Asset có sẵn chưa?
- Tech Lead có file Lottie JSON / WebP animation cho khung viền avatar không?
- Khung viền có phân cấp theo Level/Rarity không? (Ví dụ: Level 1-10 khung bạc, 11-30 khung vàng...)
- Hay chỉ cần 1 khung viền tĩnh duy nhất cho phase này?

### 4. Về `currentTitle` — Nguồn dữ liệu
- Danh hiệu được tính toán ở đâu? Server tự gán hay Client tính dựa trên level/thành tích?
- Có danh sách Enum cố định cho các danh hiệu không? (Ví dụ: "Tân Binh", "Thám Hiểm Gia", "Huyền Thoại"...)

### 5. Về `totalRolls` — Hiển thị ở đâu?
- Trường này hiển thị trên UI Profile hay chỉ phục vụ logic nội bộ (tính toán pity system)?
- Nếu hiển thị, cần thêm vào vùng nào trên layout?

### 6. Về DB Version — Chiến lược migration
- Hiện tại đang dùng Nuclear Reset (`deleteDatabase`) khi nâng version. Với 3 trường mới cần lên V3, Lead có muốn chuyển sang **Migration thật** (`addMigration(2, 3)` với `ALTER TABLE ADD COLUMN`) để giữ lại dữ liệu cache cũ không? Hay vẫn chấp nhận xóa sạch?
