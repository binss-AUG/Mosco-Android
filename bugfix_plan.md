# 🔧 Mosco Bugfix Sprint Plan

## Phase 1 — Critical Crashes (BLOCKING)

### L1: Profile Crash
**Root Cause:** `AvatarCropTransformation.equals()` gọi `cropParams.equals()` khi `cropParams` có thể null → **NPE**. Thêm vào đó, `sessionManager` bị khởi tạo 2 lần, `getContext()` bị gọi không an toàn.
**Fix:**
- Null-safe `equals()` + `hashCode()` trong `AvatarCropTransformation`
- Null-safe `SmartFaceCropTransformation` fallback
- Consolidate `sessionManager` init: chỉ init 1 lần ở `onCreateView`
- Bọc `loadAvatar()` trong try-catch + null check `getContext()`

### L1: Showcase Data Loss
**Root Cause (từ screenshot):** `DatabaseLoader.findByCollectionId()` return null → hiện "OBJ#019d" (unknown card). Master data chưa load xong khi showcase render.
**Fix:**
- `bindCardView()` phải retry load master data nếu null
- Đảm bảo `initMasterDataSync()` hoàn tất trước khi render
- Không xóa showcase IDs khi master data chưa sẵn sàng

---

## Phase 2 — Security & Session

### L2: Glow/Highlight cho selected items trong BottomSheet
**Fix:** Thêm `view_selected_overlay` có border glow vào `item_inventory_card.xml`, sync lại adapter logic.

### L4: Server offline → kick user
### L5: Dual login kick  
### L6: JWT expiry kick
**Tất cả 3 bug trên cùng 1 giải pháp:**
- **Server:** Đã có `activeToken` check trong `JwtAuthFilter` → OK
- **Client:** BẬT LẠI interceptor 401 trong `ApiClient` (đang bị comment out)
- Thay vì redirect thẳng, hiện **Dialog "Phiên đăng nhập hết hạn"** (blocking, không dismiss) → nhấn OK → về SignIn
- Tạo `SessionExpiredDialog` component dùng chung

### L3: ETL Data Versioning (Server→Client sync)
**Server:**
- Thêm field `dataVersion` (timestamp) vào response `/api/health` hoặc endpoint mới `/api/data/version`
- Khi ETL chạy xong → update `dataVersion`

**Client:**  
- Lưu `lastDataVersion` vào SharedPreferences
- Khi login/splash: gọi `/api/data/version`, so sánh → nếu mới hơn → pull master data lại

---

## Phase 3 — UI Standardization

### UI1: Đồng bộ Dialog chuẩn Mosco
- Dùng `layout_mosco_dialog_base.xml` làm base cho TẤT CẢ dialog
- Convert: `GiftActivity` confirm, `SplashActivity` cellular dialog, `showFriendActionDialog`, etc.

### UI2: Back button cho Recovery & SignUp
- Thêm `btn_back` (ImageView + ic_arrow_back) vào header của `activity_forgot_password.xml` và `activity_sign_up.xml`

### UI3: Connection Lost → Blocking Dialog (Global)
- Tạo `ConnectionLostDialog` (non-cancelable, chỉ có nút Retry + Exit)
- Observe `NetworkMonitor` ở **BaseActivity** level
- SplashActivity: dùng cơ chế riêng đã có (layout_retry_connection) → KHÔNG hiện dialog chồng
- Auth screens (SignIn/SignUp/Forgot): hiện dialog
- MainActivity: hiện dialog thay vì banner nhỏ

### UI4: Exit App Dialog chuẩn Mosco
- Đảm bảo `dialog_exit_confirm.xml` dùng `layout_mosco_dialog_base.xml`

---

## Phase 4 — Data Architecture

### L8: Room DB cho Objet Metadata
- Tạo `ObjetEntity` (Room Entity) lưu: `collectionId`, `member`, `frontImageId`, `cardClass`, `season`, `ovr`, `upgradeLevel`
- Tạo `ObjetDao` với query methods
- Migrate `DatabaseLoader` để read/write từ Room thay vì in-memory cache thuần
- Glide vẫn quản lý image cache (Room chỉ lưu metadata + image URL/ID)

### L7: Redis → SKIP (dùng Spring Cache thay thế nếu cần)

---

> [!IMPORTANT]
> Tổng cộng: **~15-20 files** cần sửa (cả client + server). Ưu tiên fix **Phase 1** trước để app không crash.
