# Liquid Glass Dark Mode — Handoff Document
> **Branch:** `style/ui-liquid-glass`  
> **Build:** ✅ SUCCESSFUL (assembleDebug)  
> **Conversation:** `5257b380-84c7-44cf-92b0-4a6fcd4c9400`  
> **Date:** 2026-05-13

---

## 1. Tổng Quan Tiến Độ

### ✅ Đã Hoàn Thành

| Phase | Mô tả | Files | Status |
|:------|:-------|------:|:------:|
| **Phase 1** | Design Token Infrastructure (colors, dimens, drawables) | 22 | ✅ |
| **Phase 2** | Architectural Shell (floating nav, transparent system bars) | 5 | ✅ |
| **Phase 3** | Core Screens migration (Home, Collection, Spin, Upgrade, Stage, Profile, Shop) | 32 | ✅ |
| **Phase 4a** | Bottom bar clearance fix (lg_content_bottom_clearance = 84dp) | 12 | ✅ |
| **Phase 4b** | Batch migration: mosco_primary → lg_accent_primary, galactic_accent → lg_accent_secondary, mosco_card_bg → lg_glass_surface_elevated | 47 | ✅ |

### Commits (5 total trên branch):
```
6e0deb2 style(ui): batch migrate mosco_primary, galactic_accent, mosco_card_bg to liquid glass tokens across 47 files
b8bf913 fix(ui): add lg_content_bottom_clearance for floating nav bar across all fragments
a89dff0 fix(ui): resolve AAPT implicit parent error in liquid glass style naming
56589fc style(ui): migrate core screens and drawables to liquid glass design tokens
38e5b88 style(ui): implement liquid glass design system phase 1-2 foundation and shell
```

---

## 2. Hệ Thống Design Tokens

### 2.1 Color Tokens (`res/values/colors.xml`)
| Token | Hex | Dùng cho |
|:------|:----|:---------|
| `lg_background` | `@color/palette_deep_space` | Nền chính toàn app |
| `lg_background_mid` | `@color/palette_deep_space_mid` | Nền mid-layer |
| `lg_background_soft` | `@color/palette_deep_space_soft` | Nền nhẹ |
| `lg_glass_surface` | `#0DFFFFFF` | Glass layer 1 (thấp nhất) |
| `lg_glass_surface_elevated` | `#14FFFFFF` | Glass layer 2 (cards, panels) |
| `lg_glass_surface_high` | `#1FFFFFFF` | Glass layer 3 (elevated cards) |
| `lg_glass_stroke` | `#26FFFFFF` | Hairline border chuẩn |
| `lg_glass_stroke_subtle` | `#1AFFFFFF` | Border nhẹ |
| `lg_glass_stroke_strong` | `#33FFFFFF` | Border nổi bật |
| `lg_accent_primary` | Electric Blue `#3A86FF` | Accent chính (CTA, indicators, tabs) |
| `lg_accent_secondary` | Cyber Purple `#8338EC` | Accent phụ (headers, badges) |
| `lg_accent_primary_dim` | `#B33A86FF` | Text dim accent |
| `lg_accent_primary_alpha_15` | `#263A86FF` | Glow nhẹ |
| `lg_accent_primary_alpha_25` | `#403A86FF` | Glow trung bình |
| `lg_nav_surface` | `#CC080C14` | Floating nav bar bg |
| `lg_nav_indicator` | `#333A86FF` | Nav active indicator |
| `lg_modal_surface` | `#E6080C14` | Dialog/BottomSheet bg |
| `lg_modal_scrim` | `#B3050710` | Modal overlay |

### 2.2 Dimension Tokens (`res/values/dimens.xml`)
| Token | Value | Dùng cho |
|:------|:------|:---------|
| `lg_radius_panel` | 20dp | Panel bo góc |
| `lg_radius_card` | 16dp | Card bo góc |
| `lg_radius_modal` | 28dp | Dialog/Modal |
| `lg_radius_button` | 14dp | Button |
| `lg_radius_nav` | 24dp | Floating nav bar |
| `lg_radius_chip` | 20dp | Filter chips |
| `lg_radius_input` | 14dp | Input fields |
| `lg_stroke_hairline` | 0.8dp | Thin hairline |
| `lg_stroke_thin` | 1dp | Standard stroke |
| `lg_nav_height` | 64dp | Nav bar height |
| `lg_nav_margin_horizontal` | 12dp | Nav margin H |
| `lg_nav_margin_bottom` | 10dp | Nav margin B |
| `lg_content_bottom_clearance` | 84dp | Content bottom padding (tránh nav che) |
| `lg_header_height` | 56dp | Header height |

### 2.3 New Drawables (`res/drawable/lg_*`)
- `lg_background_deep.xml` — Deep space gradient background
- `lg_button_primary.xml` — Primary CTA button (Electric Blue gradient)
- `lg_button_glass.xml` — Ghost/glass outline button
- `lg_nav_background.xml` — Floating nav bar shape
- `lg_nav_item_indicator.xml` — Nav active item indicator
- `lg_surface_card.xml` — Glass card surface
- `lg_surface_modal.xml` — Modal/dialog surface
- `lg_tab_indicator.xml` — Tab indicator accent

### 2.4 Styles (`res/values/styles.xml`)
- `LiquidGlass_Button` — Primary button style
- `LiquidGlass_ButtonGhost` — Ghost/outlined button
- `LiquidGlass_Chip` — Filter chip style

### 2.5 Themes (`res/values/themes.xml`)
- `LiquidGlass_DialogTheme` — Alert dialog theme
- `LiquidGlass_BottomSheetTheme` — Bottom sheet theme
- `LiquidGlass_BottomSheetStyle` — Bottom sheet modal style

> ⚠️ **QUAN TRỌNG**: Tất cả tên style dùng **underscore** (`LiquidGlass_Button`) thay vì **dot** (`LiquidGlass.Button`) để tránh AAPT implicit parent chaining error.

---

## 3. Screens Đã Fix Bottom Clearance

| Fragment | Element | Clearance Applied |
|:---------|:--------|:------------------|
| `fragment_spin.xml` | btn_spin, btn_confirm_select, btn_collect, btn_try_again | marginBottom = 84dp |
| `fragment_upgrade.xml` | btn_upgrade | marginBottom = 84dp |
| `fragment_home.xml` | layout_home_chat | marginBottom = 84dp |
| `fragment_stage.xml` | layout_bottom_indicator | marginBottom = 84dp |
| `fragment_collection_objets.xml` | rv_objets | paddingBottom = 84dp |
| `fragment_collection_items.xml` | rv_items | paddingBottom = 84dp |
| `fragment_collection_album.xml` | rv_album | paddingBottom = 84dp |
| `fragment_collection_mailbox.xml` | btn_receive_all | marginBottom = 84dp |
| `fragment_shop.xml` | rv_shop | paddingBottom = 84dp |
| `fragment_profile_general.xml` | NestedScrollView | paddingBottom = 84dp |
| `fragment_profile_trophy.xml` | NestedScrollView | paddingBottom = 84dp |

---

## 4. Còn Lại (Phase 4c+ / Phase 5)

### 4.1 Legacy `mosco_` Tokens Còn Sót (~172 references trong layouts)
Các token sau vẫn chưa được migrate — phần lớn là text colors, utility colors, và alpha variants:

**Ưu tiên cao (nên migrate):**
- `mosco_surface` → `lg_background` (2 chỗ còn sót ở non-layout files)
- `mosco_on_surface` → `@color/white` hoặc token mới `lg_text_primary`
- `mosco_on_surface_variant` → `@color/mosco_white_70` hoặc `lg_text_secondary`
- `mosco_text_dim` → token mới `lg_text_dim`
- `mosco_text_disabled` → token mới `lg_text_disabled`
- `mosco_outline` → `lg_glass_stroke` hoặc token mới `lg_outline`

**Ưu tiên thấp (utility/alpha — giữ nguyên được):**
- `mosco_white_*` (10, 15, 20, 25, 30, 40, 50, 60, 70, 80) — utility alpha colors, dùng trực tiếp
- `mosco_black_*` (10, 50, 70, 90) — overlay blacks
- `mosco_error`, `mosco_success`, `mosco_gold`, `mosco_tertiary` — semantic state colors
- `mosco_btn_disabled`, `mosco_filter_bg`, `mosco_filter_hint` — component-specific

### 4.2 Drawable `bg_global_gradient.xml`
- File gốc vẫn dùng `obsidian_start`/`obsidian_end` colors
- Đã migrate 6 layouts từ `bg_global_gradient` → `lg_background_deep`
- **Nên update nội dung file** hoặc giữ nguyên làm fallback

### 4.3 Java Code References
Chưa scan Java files cho hardcoded colors. Cần kiểm tra:
```
grep -r "mosco_primary\|galactic_accent\|Color.parse" --include="*.java" client/app/src/main/java/
```

### 4.4 Shimmer/VFX Polish (Optional Phase 5)
- Shimmer loading animation đã có framework (`ShimmerFrameLayout` trong Home, Collection)
- Card shimmer shader (metallic reflection) — reserved cho sprint sau
- Typography kerning fine-tuning — dimens_typography.xml đã sẵn sàng

### 4.5 Visual Audit Checklist
- [ ] Kiểm tra text readability trên glass surfaces (contrast ratio)
- [ ] Verify floating nav không clip content trên small screens
- [ ] Test landscape orientation (nếu cần)
- [ ] Test trên Android 9 emulator (target device)
- [ ] Kiểm tra InventoryBottomSheet clearance (modal nên OK)
- [ ] Verify dialog_exit_confirm, dialog_update_metadata hiển thị đúng

---

## 5. Cách Tiếp Tục

```bash
# 1. Checkout branch
git checkout style/ui-liquid-glass

# 2. Verify build
cd client && ./gradlew assembleDebug

# 3. Scan remaining legacy refs
cd client/app/src/main/res/layout
Select-String -Path "*.xml" -Pattern "@color/mosco_" | Measure-Object -Line

# 4. Batch replace tiếp (ví dụ mosco_surface)
# Sử dụng PowerShell pattern tương tự Phase 4b

# 5. Scan Java files
Select-String -Path "*.java" -Pattern "mosco_primary|galactic_accent" -Recurse
```

### Quy tắc khi thêm mới:
1. **Mọi color mới** phải dùng prefix `lg_` 
2. **Mọi dimension mới** phải dùng prefix `lg_`
3. **Mọi style mới** phải dùng `LiquidGlass_` (underscore, KHÔNG dùng dot)
4. **Bottom buttons** phải dùng `lg_content_bottom_clearance` (84dp)
5. **Card backgrounds** dùng `lg_glass_surface_elevated`
6. **Accent color** = `lg_accent_primary` (Electric Blue #3A86FF)

---

## 6. File Structure Summary

```
res/values/
├── colors.xml          ← 19 lg_* tokens (line 221-256)
├── dimens.xml          ← 15 lg_* tokens (line 96-126)  
├── styles.xml          ← 3 LiquidGlass_* styles (line 148-191)
└── themes.xml          ← 3 LiquidGlass_* themes (line 62-88)

res/drawable/
├── lg_background_deep.xml
├── lg_button_glass.xml
├── lg_button_primary.xml
├── lg_nav_background.xml
├── lg_nav_item_indicator.xml
├── lg_surface_card.xml
├── lg_surface_modal.xml
├── lg_tab_indicator.xml
└── bg_*.xml             ← 20+ files updated to lg_* tokens
```
