# Mosco Project — Liquid Glass UI Documentation
> **Version:** 1.1 (Global Consistency Patch)  
> **Status:** Production Ready / Unified Design System  
> **Design Philosophy:** Luxury Glass, Translucent Depth, Spring Interactions (Quiet Futuristic)

Tài liệu này hướng dẫn chi tiết về hệ thống giao diện **Liquid Glass v1.1**, được thiết kế để tạo ra trải nghiệm đồng nhất, cao cấp trên toàn bộ ứng dụng Mosco.

---

## 1. Hệ Thống Design Tokens (v1.1)

### 1.1 Corner Radius System (iOS-inspired)
Tất cả các góc bo (radius) phải tuân theo hệ thống phân cấp sau trong `res/values/dimens.xml`:

| Token | Giá trị | Đối tượng áp dụng |
|:---|:---|:---|
| `lg_radius_small_control` | 18dp | Chips, Checkbox, SearchBar mini |
| `lg_radius_button` | 22dp | Các nút bấm tiêu chuẩn (Standard Buttons) |
| `lg_radius_nav` | 28dp | Thanh Navigation nổi, Panels, Floating Bars |
| `lg_radius_panel` | 28dp | Các khu vực nội dung lớn |
| `lg_radius_modal` | 32dp | BottomSheet và các Dialog chính |

### 1.2 Glass Surface Specification (Hệ thống Layering A-B-C-D)
Mọi component tương tác phải được cấu tạo từ 4 lớp quang học:
- **Layer A (Base Glass):** Nền kính mờ (`lg_glass_surface` hoặc màu Accent alpha thấp).
- **Layer B (Soft Highlight):** Gradient trắng mờ (10-15%) ở đỉnh để tạo hiệu ứng ánh sáng phản xạ.
- **Layer C (Hairline Border):** Viền stroke 0.8dp - 1dp (`lg_glass_stroke_strong`) để tách biệt khỏi background.
- **Layer D (Inner Glow):** Một lớp viền nội bộ cực mỏng để mô phỏng sự khúc xạ ánh sáng trong kính.

---

## 2. Hệ Thống Nút Bấm Thống Nhất (Unified Button System)

CẤM sử dụng các style Material mặc định. Mọi nút bấm PHẢI kế thừa từ hệ thống Liquid Glass:

| Style Name | Drawable | Vai trò |
|:---|:---|:---|
| `LiquidGlass_Button_Primary` | `lg_btn_primary` | CTA chính (Electric Blue Glass) |
| `LiquidGlass_Button_Secondary`| `lg_btn_secondary`| CTA phụ (Translucent Deep Space) |
| `LiquidGlass_Button_Ghost` | `lg_btn_ghost` | Nút viền, nền trong suốt (Glass Outlined) |
| `LiquidGlass_IconButton` | `lg_btn_icon` | Nút Icon vuông bo góc (18dp radius) |
| `LiquidGlass_PillButton` | `lg_btn_pill` | Nút dạng viên thuốc (Stadium Shape) |

---

## 3. Interaction & Animation Rules

### 3.1 Interaction States (`lg_btn_state_animator.xml`)
Hệ thống không sử dụng Ripple mặc định quá mạnh của Android. Thay vào đó, sử dụng cơ chế **Scale-Response**:
- **Khi nhấn (Pressed):** Scale X/Y giảm về `0.96`, Alpha giảm về `0.85` (Duration: 100ms).
- **Khi thả (Released):** Scale X/Y trở về `1.0` với hiệu ứng `overshoot` nhẹ (Duration: 250ms).

### 3.2 Hướng dẫn áp dụng trong Java:
Đối với các Custom View hoặc Code-behind, áp dụng animator như sau:
```java
StateListAnimator animator = AnimatorInflater.loadStateListAnimator(context, R.animator.lg_btn_state_animator);
button.setStateListAnimator(animator);
```

---

## 4. Input Fields & Navigation

### 4.1 Glass Inputs
Không sử dụng underline kiểu Material cũ. Tất cả các input phải có style:
- Nền: `@drawable/lg_input_bg`
- Bo góc: `@dimen/lg_radius_input` (18dp)
- Style: `@style/LiquidGlass_Input`

### 4.2 Floating Navigation
Thanh điều hướng dưới đáy (Bottom Nav) phải sử dụng:
- Background: `@drawable/lg_surface_nav` (Có độ sâu Layering B và D).
- Indicator: `@drawable/lg_nav_item_indicator` (Dạng liquid capsule).
- Clearance: Luôn đảm bảo `paddingBottom="@dimen/lg_content_bottom_clearance"` (84dp) cho nội dung phía trên.

---

## 5. Quy Ước Phát Triển Sau Này (Future-Proofing)

1.  **Nhất quán Radius:** Khi tạo panel mới, hãy kiểm tra phân cấp trong `dimens.xml`. Không tự ý hardcode giá trị DP.
2.  **Độ trong suốt:** Sử dụng các dải Alpha `0x0D` (5%), `0x14` (8%), `0x26` (15%) để duy trì cảm giác "Liquid".
3.  **VFX Polish:** Kết hợp với `CardEffectHelper.java` để thêm hiệu ứng Glow cho các component cấp độ 4-5 (Dialogs/Modals).
4.  **Performance:** Luôn sử dụng `<layer-list>` cho các hiệu ứng phức tạp thay vì lồng nhiều View (Nested Layout) để giữ 60FPS trên giả lập Android 9.

---

*Tài liệu này được cập nhật vào ngày 15/05/2026 bởi Antigravity AI - Mosco Senior Architect.*
