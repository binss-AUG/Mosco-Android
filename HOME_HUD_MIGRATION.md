# 🪐 Mosco Home & Splash Optimization Documentation (V5.2)

Tài liệu này mô tả các thay đổi quan trọng trong cấu trúc `HomeFragment` và `SplashActivity` để đạt được hiệu năng Galactic UI 2026.

---

## 🚀 1. HomeFragment: Showcase & Quick Tools

### 🆕 Cấu trúc Layout mới
Thứ tự layout đã được chuẩn hóa để mang lại trải nghiệm phân tầng (Hierarchical UX):
1.  **Header**: Thông tin User & Tiền tệ.
2.  **Banner**: ViewPager2 hiển thị sự kiện.
3.  **Quick Tool Menu**: Dãy menu ngang (thay thế cho Floating HUD cũ) giúp truy cập nhanh các tính năng: Rank, Daily, PVP, Upgrade.
4.  **Showcase**: Centerpiece hiển thị thẻ Ace cao nhất.
5.  **Nav Menu**: Bottom Navigation bar (thuộc MainActivity).

### 🛠 Showcase Replication
- **Tỷ lệ vàng (1:1.54)**: Showcase card hiện sử dụng cùng tỷ lệ và cơ chế OVR/Badge với `UpgradeFragment`.
- **layout_core_card**: Sử dụng `include` để đảm bảo Single Source of Truth cho UI thẻ bài.

### 🎮 Cơ chế Gesture & Animation
- **Swipe to Flip**: Chức năng lật thẻ 180 độ được kích hoạt khi swipe ngang (trái/phải).
- **Asset Sync**: Sử dụng asset `objet_back_spin` cho mặt sau của thẻ khi flip.
- **Single Click**: Mở `UpgradeBottomSheet` để chọn thẻ showcase khác.
- **Double Click**: Mở dialog chi tiết của thẻ hiện tại.

---

## ⚡ 2. Splash Loading Optimization

### 📊 Render Monitoring System
Hệ thống tracking mới trong `SplashActivity` giúp đo lường thời gian xử lý của từng component:
- `Init`: Khởi tạo UI.
- `DatabaseLoad`: Nạp metadata thẻ bài.
- `AssetDownload`: Tải tài nguyên từ Cloudflare.
- `InventoryCache`: Nạp dữ liệu người dùng từ Server.

### 🛡 Performance Metrics (Target)
- **Total Splash Time**: Tối thiểu 2500ms (đảm bảo animation mượt mà).
- **Zero Jank**: Chuyển cảnh `fade_in/fade_out` kết hợp truyền `PlayTime` animation giữa các Activity để tránh khựng hình.

---

## ⚠️ Breaking Changes & Notes

1.  **HUD Removal**: View `fl_hud_container` đã bị loại bỏ hoàn toàn. Các logic điều hướng được chuyển sang `ll_quick_tools`.
2.  **Resource Loading**: Theo quy tắc "No Hardcode", các background và màu sắc của Quick Tool được set thông qua Java code thay vì XML để đảm bảo tính linh hoạt.
3.  **Asset Dependencies**: Yêu cầu asset `objet_back_spin.png` phải tồn tại trong thư mục `drawable`.

---
*© 2026 Mosco Project - Advanced Agentic Coding Team.*
